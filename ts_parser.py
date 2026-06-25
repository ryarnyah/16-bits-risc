#!/usr/bin/env python3
"""Tree-sitter-based C99 parser producing AST compatible with cc.py's CGen."""

import sys
import re

from cc import (
    Node, Program, Type, VarDecl, ParamDecl, FunctionDecl,
    Block, IfStmt, WhileStmt, DoWhileStmt, ForStmt,
    ReturnStmt, BreakStmt, ContinueStmt, ExprStmt,
    Const, StringLit, VarRef, UnaryOp, BinaryOp, Assignment,
    FuncCall, ArraySub, CastExpr, CondExpr, InitList, SizeofExpr,
    TOK_PLUS, TOK_MINUS, TOK_STAR, TOK_SLASH, TOK_PERCENT,
    TOK_AND, TOK_PIPE, TOK_CARET,
    TOK_LSH, TOK_RSH,
    TOK_LT, TOK_GT, TOK_LE, TOK_GE,
    TOK_EQ, TOK_NE,
    TOK_LAND, TOK_LOR,
    TOK_DOT, TOK_ARROW,
)


_BINOP = {
    '+': TOK_PLUS, '-': TOK_MINUS, '*': TOK_STAR, '/': TOK_SLASH,
    '%': TOK_PERCENT, '<<': TOK_LSH, '>>': TOK_RSH,
    '<': TOK_LT, '>': TOK_GT, '<=': TOK_LE, '>=': TOK_GE,
    '==': TOK_EQ, '!=': TOK_NE,
    '&': TOK_AND, '|': TOK_PIPE, '^': TOK_CARET,
    '&&': TOK_LAND, '||': TOK_LOR,
    '.': TOK_DOT, '->': TOK_ARROW,
}

_SKIP_TYPES = frozenset({
    'type_qualifier', 'storage_class_specifier',
    'comment', ';', ',', '(', ')', '{', '}', '[', ']',
})

_DECLARATOR_TYPES = frozenset({
    'identifier', 'pointer_declarator', 'array_declarator',
    'function_declarator', 'parenthesized_declarator',
    'init_declarator',
})

_TYPE_SPEC_TYPES = frozenset({
    'primitive_type', 'sized_type_specifier', 'type_identifier',
    'struct_specifier', 'enum_specifier',
})


class TSParser:
    def __init__(self, source, filename='<stdin>'):
        self.source = source
        self._source_bytes = source.encode('utf-8')
        self.filename = filename
        self.strlits = []
        self._strlit_idx = 0
        self.enum_consts = {}
        self._errors = []

        import tree_sitter_c as tsc
        from tree_sitter import Language, Parser as _TSParser
        LANG = Language(tsc.language())
        self._parser = _TSParser(LANG)
        self._tree = self._parser.parse(self._source_bytes)
        self._root = self._tree.root_node

    def _text(self, node):
        return self._source_bytes[node.start_byte:node.end_byte].decode('utf-8')

    def _error(self, msg, node=None):
        if node:
            line, col = node.start_point
            self._errors.append(f'{self.filename}:{line}:{col}: {msg}')
        else:
            self._errors.append(msg)

    # ── String/char helpers ──

    def _escape_decode(self, seq):
        if len(seq) < 2 or seq[0] != '\\':
            return seq
        c = seq[1]
        if c == 'a': return '\a'
        if c == 'b': return '\b'
        if c == 'f': return '\f'
        if c == 'n': return '\n'
        if c == 'r': return '\r'
        if c == 't': return '\t'
        if c == 'v': return '\v'
        if c == '\\': return '\\'
        if c == "'": return "'"
        if c == '"': return '"'
        if c == '?': return '?'
        if c == '0':
            if len(seq) > 2 and seq[2] in '01234567':
                val = 0
                for ch in seq[1:]:
                    if ch not in '01234567':
                        break
                    val = val * 8 + int(ch)
                return chr(val & 0xFFFF)
            return '\0'
        if c in ('x', 'X'):
            val = 0
            for ch in seq[2:]:
                if ch not in '0123456789abcdefABCDEF':
                    break
                val = val * 16 + int(ch, 16)
            return chr(val & 0xFFFF)
        if c in '01234567':
            val = 0
            for ch in seq[1:]:
                if ch not in '01234567':
                    break
                val = val * 8 + int(ch)
            return chr(val & 0xFFFF)
        return seq

    def _collect_string_content(self, node):
        chars = []
        for child in node.children:
            if child.type == 'string_content':
                chars.append(self._text(child))
            elif child.type == 'escape_sequence':
                chars.append(self._escape_decode(self._text(child)))
        return ''.join(chars)

    def _collect_string(self, node):
        if node.type == 'concatenated_string':
            val = ''.join(
                self._collect_string_content(c)
                for c in node.children if c.type == 'string_literal'
            )
        else:
            val = self._collect_string_content(node)
        lbl = f'.Lstr{self._strlit_idx}'
        self._strlit_idx += 1
        self.strlits.append((lbl, val))
        return StringLit(val, lbl)

    def _walk_char_literal(self, node):
        for child in node.children:
            if child.type == 'character':
                return Const(ord(self._text(child)) & 0xFFFF)
            if child.type == 'escape_sequence':
                decoded = self._escape_decode(self._text(child))
                return Const(ord(decoded) & 0xFFFF if decoded else 0)
        return Const(0)

    # ── Type specifier extraction ──

    def _collect_type_spec(self, node):
        """Extract type spec string from declaration/parameter_declaration/
        type_descriptor node, skipping qualifiers and storage classes."""
        parts = []
        for child in node.children:
            t = child.type
            if t in _SKIP_TYPES:
                continue
            if t in _TYPE_SPEC_TYPES:
                parts.append(self._type_spec_text(child))
                continue
            if t in _DECLARATOR_TYPES or t == 'abstract_pointer_declarator':
                break
        if not parts:
            return None
        spec = ' '.join(parts)
        return self._normalize_spec(spec)

    def _type_spec_text(self, node):
        t = node.type
        if t == 'primitive_type':
            return self._text(node)
        if t == 'sized_type_specifier':
            sub = []
            for c in node.children:
                if c.is_named:
                    sub.append(self._text(c))
            return ' '.join(sub) if sub else self._text(node)
        if t == 'type_identifier':
            return self._text(node)
        if t == 'struct_specifier':
            name = ''
            for c in node.children:
                if c.type == 'type_identifier':
                    name = self._text(c)
                    break
            return f'struct {name}' if name else 'struct'
        if t == 'enum_specifier':
            self._process_enum(node)
            return 'int'
        return self._text(node)

    def _normalize_spec(self, spec):
        m = {
            'signed int': 'int',
            'signed': 'int',
            'unsigned': 'unsigned int',
            'signed char': 'char',
            'unsigned char': 'unsigned char',
            'unsigned int': 'unsigned int',
        }
        return m.get(spec, spec)

    def _process_enum(self, node):
        enum_list = None
        for c in node.children:
            if c.type == 'enumerator_list':
                enum_list = c
                break
        if enum_list is None:
            return
        val = 0
        for c in enum_list.children:
            if c.type == 'enumerator':
                name = None
                init = None
                for cc in c.children:
                    if cc.type == 'identifier':
                        name = self._text(cc)
                    elif cc.type == 'number_literal':
                        try:
                            init = int(self._text(cc), 0)
                        except ValueError:
                            pass
                if name:
                    if init is not None:
                        val = init
                    self.enum_consts[name] = val
                    val += 1

    # ── Declarator walker ──

    def _walk_declarator(self, node, base_ptr=0):
        """Walk a declarator tree; return (name, ptr, array_size, is_func, params, init_node)."""
        t = node.type
        if t == 'identifier':
            return (self._text(node), base_ptr, 0, False, [], None)
        if t == 'pointer_declarator':
            inner = None
            for c in node.children:
                if c.type in ('identifier', 'pointer_declarator', 'array_declarator',
                              'function_declarator', 'parenthesized_declarator'):
                    inner = c
                    break
            if inner:
                return self._walk_declarator(inner, base_ptr + 1)
            return (None, base_ptr + 1, 0, False, [], None)
        if t == 'array_declarator':
            inner = None
            size = 0
            for c in node.children:
                if c.type in ('identifier', 'pointer_declarator', 'array_declarator',
                              'function_declarator', 'parenthesized_declarator'):
                    inner = c
                elif c.type == 'number_literal' and inner is not None:
                    try:
                        size = int(self._text(c), 0)
                    except ValueError:
                        pass
            if inner:
                name, ptr, _, _, _, i = self._walk_declarator(inner, 0)
                return (name, ptr + 1, size, False, [], i)
            return (None, 0, size, False, [], None)
        if t == 'function_declarator':
            inner = None
            params = []
            for c in node.children:
                if c.type == 'parameter_list':
                    params = self._walk_params(c)
                elif c.type in ('identifier', 'pointer_declarator', 'parenthesized_declarator'):
                    inner = c
            if inner:
                name, ptr, _, _, _, i = self._walk_declarator(inner, base_ptr)
                return (name, ptr, 0, True, params, i)
            return (None, base_ptr, 0, True, params, None)
        if t == 'parenthesized_declarator':
            for c in node.children:
                if c.type in ('identifier', 'pointer_declarator', 'array_declarator',
                              'function_declarator', 'parenthesized_declarator'):
                    return self._walk_declarator(c, base_ptr)
            return (None, base_ptr, 0, False, [], None)
        if t == 'init_declarator':
            inner = None
            init = None
            for c in node.children:
                ct = c.type
                if ct in ('identifier', 'pointer_declarator', 'array_declarator',
                          'function_declarator', 'parenthesized_declarator'):
                    if inner is None:
                        inner = c
                elif ct == '=':
                    continue
                elif inner is not None and c.is_named:
                    init = c
            if inner:
                name, ptr, asize, is_func, params, _ = self._walk_declarator(inner, base_ptr)
                return (name, ptr, asize, is_func, params, init)
            return (None, base_ptr, 0, False, [], init)
        if t == 'abstract_pointer_declarator':
            # Used in unnamed parameters and casts e.g. int*
            ptr = 0
            for c in node.children:
                if c.type == 'abstract_pointer_declarator':
                    ptr += 1
            return (None, base_ptr + ptr + 1, 0, False, [], None)
        return (None, base_ptr, 0, False, [], None)

    def _walk_params(self, node):
        params = []
        for child in node.children:
            if child.type == 'parameter_declaration':
                p = self._walk_param_decl(child)
                if p:
                    params.append(p)
        return params

    def _walk_param_decl(self, node):
        spec = self._collect_type_spec(node) or 'int'
        name = ''
        ptr = 0
        for child in node.children:
            t = child.type
            if t in ('identifier', 'pointer_declarator', 'array_declarator',
                      'function_declarator', 'parenthesized_declarator'):
                info = self._walk_declarator(child)
                if info and info[0]:
                    name = info[0]
                    ptr = info[1]
                    break
            elif t == 'abstract_pointer_declarator':
                ptr += 1
        return ParamDecl(Type(spec, ptr), name)

    def _parse_type_descriptor(self, node):
        """Parse type_descriptor for sizeof/cast types."""
        if node is None:
            return None
        spec = self._collect_type_spec(node)
        if spec is None:
            return None
        ptr = 0
        for child in node.children:
            if child.type == 'abstract_pointer_declarator':
                ptr += 1
        return Type(spec, ptr)

    # ── Top-level ──

    def parse(self):
        prog = Program()
        for child in self._root.children:
            if child.type == 'function_definition':
                f = self._walk_function_definition(child)
                if f:
                    prog.decls.append(f)
            elif child.type == 'declaration':
                for d in self._walk_declaration(child, True):
                    prog.decls.append(d)
        return prog

    def _walk_function_definition(self, node):
        spec = None
        func_dec = None
        body = None
        spec_parts = []
        for child in node.children:
            t = child.type
            if t in _SKIP_TYPES:
                continue
            if t in _TYPE_SPEC_TYPES:
                spec_parts.append(self._type_spec_text(child))
            elif t == 'function_declarator':
                func_dec = child
            elif t == 'compound_statement':
                body = child
        spec = self._normalize_spec(' '.join(spec_parts)) if spec_parts else 'int'
        if func_dec is None:
            return None
        name = ''
        ptr = 0
        params = []
        for child in func_dec.children:
            if child.type == 'identifier':
                name = self._text(child)
            elif child.type == 'pointer_declarator':
                info = self._walk_declarator(child)
                if info and info[0]:
                    name = info[0]
                    ptr = info[1]
            elif child.type == 'parameter_list':
                params = self._walk_params(child)
        if not name:
            return None
        rt = Type(spec, ptr)
        body_block = self._walk_block(body) if body else None
        return FunctionDecl(rt, name, params, body_block)

    def _walk_declaration(self, node, is_global):
        spec = self._collect_type_spec(node)
        if spec is None:
            return []
        decls = []
        for child in node.children:
            t = child.type
            if t in _SKIP_TYPES or t in _TYPE_SPEC_TYPES:
                continue
            if t not in _DECLARATOR_TYPES:
                continue
            info = self._walk_declarator(child)
            if not info or not info[0]:
                continue
            name, ptr, asize, is_func, params, init_node = info
            if is_func:
                continue
            typ = Type(spec, ptr)
            init = self._walk_expr(init_node) if init_node else None
            d = VarDecl(typ, name, init, not is_global)
            d.is_global = is_global
            d.array_size = asize
            decls.append(d)
        return decls

    # ── Block / Statement walkers ──

    def _walk_block(self, node):
        b = Block()
        if node is None:
            return b
        for child in node.children:
            if child.type in ('{', '}'):
                continue
            s = self._walk_stmt(child)
            if s:
                b.stmts.append(s)
        return b

    def _walk_stmt(self, node):
        if node is None:
            return None
        t = node.type
        if t == 'if_statement':
            return self._walk_if_stmt(node)
        if t == 'while_statement':
            return self._walk_while_stmt(node)
        if t == 'do_statement':
            return self._walk_do_stmt(node)
        if t == 'for_statement':
            return self._walk_for_stmt(node)
        if t == 'return_statement':
            return self._walk_return_stmt(node)
        if t == 'break_statement':
            return BreakStmt()
        if t == 'continue_statement':
            return ContinueStmt()
        if t == 'expression_statement':
            return self._walk_expr_stmt(node)
        if t == 'compound_statement':
            return self._walk_block(node)
        if t == 'declaration':
            return self._walk_decl_stmt(node)
        if t == 'switch_statement':
            return self._walk_switch_stmt(node)
        return None

    def _walk_decl_stmt(self, node):
        decls = self._walk_declaration(node, False)
        if len(decls) == 1:
            return decls[0]
        if decls:
            b = Block()
            b.stmts = decls
            return b
        return None

    def _walk_if_stmt(self, node):
        cond = None
        then = None
        els = None
        for child in node.children:
            t = child.type
            if t == 'parenthesized_expression':
                cond = self._walk_expr(
                    next((c for c in child.children if c.is_named), None)
                )
            elif t == 'else_clause':
                for c in child.children:
                    if c.is_named:
                        els = self._walk_stmt(c)
                        break
            elif t in ('compound_statement', 'expression_statement',
                        'if_statement', 'while_statement', 'for_statement',
                        'do_statement', 'return_statement', 'break_statement',
                        'continue_statement', 'switch_statement',
                        'declaration'):
                if then is None:
                    then = self._walk_stmt(child)
                else:
                    els = self._walk_stmt(child)
        return IfStmt(cond, then, els)

    def _walk_while_stmt(self, node):
        cond = None
        body = None
        for child in node.children:
            if child.type == 'parenthesized_expression':
                cond = self._walk_expr(
                    next((c for c in child.children if c.is_named), None)
                )
            elif child.is_named and child.type not in ('while',):
                body = self._walk_stmt(child)
        return WhileStmt(cond, body)

    def _walk_do_stmt(self, node):
        body = None
        cond = None
        for child in node.children:
            if child.type == 'parenthesized_expression':
                cond = self._walk_expr(
                    next((c for c in child.children if c.is_named), None)
                )
            elif child.is_named and child.type not in ('do', 'while'):
                body = self._walk_stmt(child)
        return DoWhileStmt(body, cond)

    def _walk_for_stmt(self, node):
        init = None
        cond = None
        inc = None
        body = None
        phase = 0
        for child in node.children:
            t = child.type
            if t in ('for', '(', ')', ';'):
                if t == ';':
                    phase += 1
                continue
            if t == 'declaration':
                d = self._walk_declaration(child, False)
                if len(d) == 1:
                    init = d[0]
                elif d:
                    b = Block()
                    b.stmts = d
                    init = b
                continue
            if phase == 0:
                init = self._walk_expr(child)
            elif phase == 1:
                cond = self._walk_expr(child)
            elif phase == 2:
                inc = self._walk_expr(child)
            else:
                body = self._walk_stmt(child)
        if body is None:
            body = Block()
        return ForStmt(init, cond, inc, body)

    def _walk_return_stmt(self, node):
        for child in node.children:
            if child.is_named and child.type != 'return':
                return ReturnStmt(self._walk_expr(child))
        return ReturnStmt(None)

    def _walk_expr_stmt(self, node):
        for child in node.children:
            if child.is_named:
                e = self._walk_expr(child)
                return ExprStmt(e)
        return ExprStmt(None)

    def _walk_switch_stmt(self, node):
        expr = None
        cases = []
        default_stmts = []
        for child in node.children:
            if child.type == 'parenthesized_expression':
                for c in child.children:
                    if c.is_named:
                        expr = self._walk_expr(c)
                        break
            elif child.type == 'compound_statement':
                for c in child.children:
                    if c.type == 'case_statement':
                        val_node = None
                        stmts = []
                        for cc in c.children:
                            if cc.type == 'number_literal':
                                val_node = cc
                            elif cc.is_named and cc.type not in (
                                'number_literal', 'case', 'default',
                            ):
                                s = self._walk_stmt(cc)
                                if s:
                                    stmts.append(s)
                        if val_node is not None:
                            v = int(self._text(val_node), 0) & 0xFFFF
                            cases.append((Const(v), stmts))
                        else:
                            default_stmts = stmts
        if not cases:
            if default_stmts:
                result = Block()
                result.stmts = default_stmts
                return result
            return ExprStmt(None)
        last = Block()
        if default_stmts:
            last.stmts = default_stmts
        for v, stmts in reversed(cases):
            blk = Block()
            blk.stmts = stmts
            cmp = BinaryOp(TOK_EQ, expr, v)
            last = IfStmt(cmp, blk, last)
        return last

    # ── Expression walker ──

    def _walk_expr(self, node):
        if node is None:
            return None
        t = node.type

        if t == 'number_literal':
            txt = self._text(node)
            # Strip C integer suffixes (u, U, l, L, ll, LL, ul, UL, etc.)
            txt = re.sub(r'(?:[uU][lL]?|[lL][lL]?[uU]?)$', '', txt)
            try:
                return Const(int(txt, 0) & 0xFFFF)
            except ValueError:
                return Const(0)

        if t == 'char_literal':
            return self._walk_char_literal(node)

        if t in ('string_literal', 'concatenated_string'):
            return self._collect_string(node)

        if t == 'identifier':
            txt = self._text(node)
            if txt in self.enum_consts:
                return Const(self.enum_consts[txt])
            return VarRef(txt)

        if t == 'true':
            return Const(1)
        if t == 'false':
            return Const(0)

        if t == 'parenthesized_expression':
            for child in node.children:
                if child.is_named:
                    return self._walk_expr(child)
            return None

        if t == 'binary_expression':
            left = None
            right = None
            op_txt = None
            for child in node.children:
                if not child.is_named:
                    op_txt = self._text(child)
                elif left is None:
                    left = self._walk_expr(child)
                else:
                    right = self._walk_expr(child)
            op = _BINOP.get(op_txt, op_txt)
            return BinaryOp(op, left, right)

        if t == 'assignment_expression':
            left = None
            right = None
            op_txt = '='
            for child in node.children:
                if not child.is_named:
                    op_txt = self._text(child)
                elif left is None:
                    left = self._walk_expr(child)
                else:
                    right = self._walk_expr(child)
            return Assignment(op_txt, left, right)

        if t == 'update_expression':
            arg = None
            op_txt = None
            for child in node.children:
                if child.is_named:
                    arg = self._walk_expr(child)
                else:
                    op_txt = self._text(child)
            if arg and op_txt:
                if node.children and not node.children[0].is_named:
                    return UnaryOp(f'{op_txt}pre', arg)
                else:
                    return UnaryOp(f'{op_txt}post', arg)
            return arg

        if t == 'pointer_expression':
            op = None
            arg = None
            for child in node.children:
                if not child.is_named:
                    op = self._text(child)
                else:
                    arg = self._walk_expr(child)
            return UnaryOp(op, arg) if op else arg

        if t == 'unary_expression':
            op = None
            arg = None
            for child in node.children:
                if not child.is_named:
                    op = self._text(child)
                else:
                    arg = self._walk_expr(child)
            return UnaryOp(op, arg) if op else arg

        if t == 'cast_expression':
            typ_node = None
            expr_node = None
            for child in node.children:
                if child.type == 'type_descriptor':
                    typ_node = child
                elif child.is_named and child.type != 'type_descriptor':
                    expr_node = child
            if typ_node:
                typ = self._parse_type_descriptor(typ_node)
                if typ:
                    return CastExpr(typ, self._walk_expr(expr_node))
            return self._walk_expr(expr_node)

        if t == 'conditional_expression':
            cond = None
            then = None
            els = None
            phase = 0
            for child in node.children:
                if not child.is_named:
                    continue
                if phase == 0:
                    cond = self._walk_expr(child)
                    phase = 1
                elif phase == 1:
                    then = self._walk_expr(child)
                    phase = 2
                elif phase == 2:
                    els = self._walk_expr(child)
            return CondExpr(cond, then, els)

        if t == 'sizeof_expression':
            for child in node.children:
                if child.type == 'type_descriptor':
                    typ = self._parse_type_descriptor(child)
                    return SizeofExpr(typ=typ or Type('int'))
                if child.is_named:
                    return SizeofExpr(expr=self._walk_expr(child))
            return SizeofExpr(typ=Type('int'))

        if t == 'call_expression':
            fn_node = None
            args = []
            for child in node.children:
                if child.type == 'argument_list':
                    for c in child.children:
                        if c.is_named and c.type != ',':
                            args.append(self._walk_expr(c))
                elif child.is_named:
                    fn_node = child
            if fn_node:
                fn = self._walk_expr(fn_node)
                if isinstance(fn, VarRef):
                    return FuncCall(fn.name, args)
                return FuncCall('(*fn)', [fn] + args)
            return FuncCall('', args)

        if t == 'subscript_expression':
            arr = None
            idx = None
            for child in node.children:
                if child.type in ('[', ']'):
                    continue
                if arr is None and child.is_named:
                    arr = self._walk_expr(child)
                elif child.is_named:
                    idx = self._walk_expr(child)
            return ArraySub(arr, idx)

        if t == 'field_expression':
            arg = None
            field = None
            op = None
            for child in node.children:
                if child.is_named and child.type == 'field_identifier':
                    field = VarRef(self._text(child))
                elif child.is_named:
                    arg = self._walk_expr(child)
                else:
                    op_text = self._text(child)
                    op = _BINOP.get(op_text, op_text)
            if op is None:
                return arg
            return BinaryOp(op, arg, field)

        return None
