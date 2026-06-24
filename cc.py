#!/usr/bin/env python3
"""C99 compiler for 16-bit RISC core -- optimized for code size.

Generates assembly (.asm), then assembles to hex (.hex).

ABI:
  R1 = accumulator / return value
  R2 = temp register
  R3 = arg 2 / temp
  R4 = arg 3 / temp
  R5 = link register (return address)
  R6 = frame pointer
  R7 = stack pointer

Args in R2 (arg1), R3 (arg2), R4 (arg3).  Args beyond 3 on stack.
"""

import sys, re, os, argparse, subprocess

# ──────────────────────────────────────────────────────────────────────
# Tokenizer
# ──────────────────────────────────────────────────────────────────────

TOK_EOF = -1
TOK_ID  = 0
TOK_NUM = 1
TOK_STR = 2
TOK_CHR = 3
TOK_SEMI = 4
TOK_LBRACE = 5
TOK_RBRACE = 6
TOK_LPAREN = 7
TOK_RPAREN = 8
TOK_LBRACK = 9
TOK_RBRACK = 10
TOK_COMMA = 11
TOK_ASSIGN = 12
TOK_PLUS = 13
TOK_MINUS = 14
TOK_STAR = 15
TOK_SLASH = 16
TOK_PERCENT = 17
TOK_AND = 18
TOK_PIPE = 19
TOK_CARET = 20
TOK_TILDE = 21
TOK_LSH = 22
TOK_RSH = 23
TOK_LT = 24
TOK_GT = 25
TOK_LE = 26
TOK_GE = 27
TOK_EQ = 28
TOK_NE = 29
TOK_LAND = 30
TOK_LOR = 31
TOK_LNOT = 32
TOK_INC = 33
TOK_DEC = 34
TOK_ADD_ASSIGN = 35
TOK_SUB_ASSIGN = 36
TOK_MUL_ASSIGN = 37
TOK_DIV_ASSIGN = 38
TOK_MOD_ASSIGN = 39
TOK_AND_ASSIGN = 40
TOK_OR_ASSIGN = 41
TOK_XOR_ASSIGN = 42
TOK_LSH_ASSIGN = 43
TOK_RSH_ASSIGN = 44
TOK_DOT = 45
TOK_ARROW = 46
TOK_QUES = 47
TOK_COLON = 48
TOK_SIZEOF = 49

TOK_IF = 'if'
TOK_ELSE = 'else'
TOK_WHILE = 'while'
TOK_FOR = 'for'
TOK_DO = 'do'
TOK_RETURN = 'return'
TOK_SWITCH = 'switch'
TOK_CASE = 'case'
TOK_DEFAULT = 'default'
TOK_BREAK = 'break'
TOK_CONTINUE = 'continue'
TOK_STRUCT = 'struct'
TOK_ENUM = 'enum'
TOK_TYPEDEF = 'typedef'
TOK_VOLATILE = 'volatile'
TOK_STATIC = 'static'
TOK_EXTERN = 'extern'
TOK_CONST = 'const'
TOK_SIGNED = 'signed'
TOK_UNSIGNED = 'unsigned'
TOK_VOID = 'void'
TOK_INT = 'int'
TOK_CHAR = 'char'
TOK_SHORT = 'short'
TOK_LONG = 'long'

KW = {'if': TOK_IF, 'else': TOK_ELSE, 'while': TOK_WHILE, 'for': TOK_FOR,
      'do': TOK_DO, 'return': TOK_RETURN, 'switch': TOK_SWITCH,
      'case': TOK_CASE, 'default': TOK_DEFAULT, 'break': TOK_BREAK,
      'continue': TOK_CONTINUE, 'struct': TOK_STRUCT, 'enum': TOK_ENUM,
      'typedef': TOK_TYPEDEF, 'volatile': TOK_VOLATILE, 'static': TOK_STATIC,
      'extern': TOK_EXTERN, 'const': TOK_CONST, 'signed': TOK_SIGNED,
      'unsigned': TOK_UNSIGNED, 'void': TOK_VOID, 'int': TOK_INT,
      'char': TOK_CHAR, 'short': TOK_SHORT, 'long': TOK_LONG,
      'sizeof': TOK_SIZEOF}

TOK2 = {'==': TOK_EQ, '!=': TOK_NE, '<=': TOK_LE, '>=': TOK_GE,
        '<<': TOK_LSH, '>>': TOK_RSH, '&&': TOK_LAND, '||': TOK_LOR,
        '++': TOK_INC, '--': TOK_DEC, '->': TOK_ARROW,
        '+=': TOK_ADD_ASSIGN, '-=': TOK_SUB_ASSIGN, '*=': TOK_MUL_ASSIGN,
        '/=': TOK_DIV_ASSIGN, '%=': TOK_MOD_ASSIGN, '&=': TOK_AND_ASSIGN,
        '|=': TOK_OR_ASSIGN, '^=': TOK_XOR_ASSIGN, '<<=': TOK_LSH_ASSIGN,
        '>>=': TOK_RSH_ASSIGN}

TOK1 = {';': TOK_SEMI, '{': TOK_LBRACE, '}': TOK_RBRACE,
        '(': TOK_LPAREN, ')': TOK_RPAREN, '[': TOK_LBRACK, ']': TOK_RBRACK,
        ',': TOK_COMMA, '?': TOK_QUES, ':': TOK_COLON,
        '+': TOK_PLUS, '-': TOK_MINUS, '*': TOK_STAR, '/': TOK_SLASH,
        '%': TOK_PERCENT, '&': TOK_AND, '|': TOK_PIPE, '^': TOK_CARET,
        '~': TOK_TILDE, '<': TOK_LT, '>': TOK_GT, '=': TOK_ASSIGN,
        '.': TOK_DOT, '!': TOK_LNOT}


class Token:
    __slots__ = ('kind', 'val', 'line', 'col')
    def __init__(self, kind, val=None, line=0, col=0):
        self.kind = kind
        self.val = val
        self.line = line
        self.col = col


class Tokenizer:
    def __init__(self, text, fn='<stdin>'):
        self.txt = text
        self.fn = fn
        self.pos = 0
        self.line = 1
        self.col = 1
        self.toks = []
        self._run()

    def _err(self, msg):
        raise SyntaxError(f'{self.fn}:{self.line}:{self.col}: {msg}')

    def _peek(self, n=0):
        i = self.pos + n
        return self.txt[i] if i < len(self.txt) else '\0'

    def _adv(self):
        c = self.txt[self.pos]
        self.pos += 1
        if c == '\n':
            self.line += 1
            self.col = 1
        else:
            self.col += 1
        return c

    def _skip_ws(self):
        while self.pos < len(self.txt) and self._peek() in ' \t\n\r\f\v':
            self._adv()

    def _skip_line(self):
        while self.pos < len(self.txt) and self._peek() != '\n':
            self._adv()

    def _esc(self):
        c = self._peek()
        if c == 'n': self._adv(); return '\n'
        if c == 't': self._adv(); return '\t'
        if c == 'r': self._adv(); return '\r'
        if c == '0': self._adv(); return '\0'
        if c == '\\': self._adv(); return '\\'
        if c == "'": self._adv(); return "'"
        if c == '"': self._adv(); return '"'
        return self._adv()

    def _run(self):
        while self.pos < len(self.txt):
            self._skip_ws()
            if self.pos >= len(self.txt):
                break
            c = self._peek()
            line = self.line
            col = self.col

            # Comments
            if c == '/' and self._peek(1) in ('/', '*'):
                if self._peek(1) == '/':
                    self._skip_line()
                else:
                    self._adv()
                    self._adv()
                    while self.pos + 1 < len(self.txt):
                        if self._peek() == '*' and self._peek(1) == '/':
                            self._adv()
                            self._adv()
                            break
                        self._adv()
                continue

            # Preprocessor
            if c == '#':
                self._skip_line()
                continue

            # Identifiers/keywords
            if c.isalpha() or c == '_':
                start = self.pos
                while self.pos < len(self.txt) and (self._peek().isalnum() or self._peek() == '_'):
                    self._adv()
                w = self.txt[start:self.pos]
                self.toks.append(Token(KW.get(w, TOK_ID), w, line, col))
                continue

            # Numbers
            if c.isdigit() or (c == '0' and self._peek(1) in 'xXbB'):
                start = self.pos
                if c == '0' and self._peek(1) in 'xX':
                    self._adv(); self._adv()
                    while self.pos < len(self.txt) and (self._peek().isdigit() or self._peek() in 'abcdefABCDEF'):
                        self._adv()
                elif c == '0' and self._peek(1) in 'bB':
                    self._adv(); self._adv()
                    while self.pos < len(self.txt) and self._peek() in '01':
                        self._adv()
                else:
                    while self.pos < len(self.txt) and self._peek().isdigit():
                        self._adv()
                if self._peek() in 'uUlL':
                    self._adv()
                    if self._peek() in 'lL':
                        self._adv()
                self.toks.append(Token(TOK_NUM, self.txt[start:self.pos], line, col))
                continue

            # Character literal
            if c == "'":
                self._adv()
                ch = self._esc() if self._peek() == '\\' else self._adv()
                if self._peek() != "'":
                    self._err("Expected '")
                self._adv()
                self.toks.append(Token(TOK_CHR, str(ord(ch)), line, col))
                continue

            # String literal
            if c == '"':
                self._adv()
                s = []
                while self._peek() != '"':
                    s.append(self._esc() if self._peek() == '\\' else self._adv())
                self._adv()
                self.toks.append(Token(TOK_STR, ''.join(s), line, col))
                continue

            # Two-char operators
            two = c + self._peek(1) if self.pos + 1 < len(self.txt) else ''
            if two in TOK2:
                self._adv()
                self._adv()
                self.toks.append(Token(TOK2[two], two, line, col))
                continue

            # Single-char operators
            if c in TOK1:
                self._adv()
                self.toks.append(Token(TOK1[c], c, line, col))
                continue

            self._err(f"Unexpected char {c!r}")

        self.toks.append(Token(TOK_EOF, None, self.line, self.col))


# ──────────────────────────────────────────────────────────────────────
# AST Nodes
# ──────────────────────────────────────────────────────────────────────

class Node:
    pass


class Program(Node):
    def __init__(self):
        self.decls = []


class Type(Node):
    def __init__(self, spec='int', ptr=0):
        self.spec = spec
        self.ptr = ptr

    def size(self):
        return 2 if self.ptr > 0 or self.spec != 'void' else 0

    def is_ptr(self):
        return self.ptr > 0

    def with_ptr(self):
        return Type(self.spec, self.ptr + 1)

    def __repr__(self):
        return self.spec + '*' * self.ptr


class VarDecl(Node):
    def __init__(self, typ, name, init=None, is_local=False):
        self.typ = typ
        self.name = name
        self.init = init
        self.is_local = is_local
        self.is_param = False
        self.is_global = False
        self.off = 0
        self.array_size = 0


class ParamDecl(Node):
    def __init__(self, typ, name):
        self.typ = typ
        self.name = name


class FunctionDecl(Node):
    def __init__(self, rt, name, params, body, is_forward=False):
        self.rt = rt
        self.name = name
        self.params = params
        self.body = body
        self.is_forward = is_forward


class Block(Node):
    def __init__(self):
        self.stmts = []


class IfStmt(Node):
    def __init__(self, cond, then, els=None):
        self.cond = cond
        self.then = then
        self.els = els


class WhileStmt(Node):
    def __init__(self, cond, body):
        self.cond = cond
        self.body = body


class DoWhileStmt(Node):
    def __init__(self, body, cond):
        self.body = body
        self.cond = cond


class ForStmt(Node):
    def __init__(self, init, cond, inc, body):
        self.init = init
        self.cond = cond
        self.inc = inc
        self.body = body


class ReturnStmt(Node):
    def __init__(self, expr=None):
        self.expr = expr


class BreakStmt(Node):
    pass


class ContinueStmt(Node):
    pass


class ExprStmt(Node):
    def __init__(self, expr=None):
        self.expr = expr


class Const(Node):
    def __init__(self, v, typ=None):
        self.v = v & 0xFFFF
        self.typ = typ or Type('int')

    def is_zero(self):
        return self.v == 0


class StringLit(Node):
    def __init__(self, v, lbl):
        self.v = v
        self.lbl = lbl


class VarRef(Node):
    def __init__(self, name):
        self.name = name
        self.decl = None


class UnaryOp(Node):
    def __init__(self, op, expr):
        self.op = op
        self.expr = expr


class BinaryOp(Node):
    def __init__(self, op, left, right):
        self.op = op
        self.left = left
        self.right = right


class Assignment(Node):
    def __init__(self, op, lhs, rhs):
        self.op = op
        self.lhs = lhs
        self.rhs = rhs


class FuncCall(Node):
    def __init__(self, name, args):
        self.name = name
        self.args = args


class ArraySub(Node):
    def __init__(self, arr, idx):
        self.arr = arr
        self.idx = idx


class CastExpr(Node):
    def __init__(self, typ, expr):
        self.typ = typ
        self.expr = expr


class CondExpr(Node):
    def __init__(self, cond, t, e):
        self.cond = cond
        self.t = t
        self.e = e


class SizeofExpr(Node):
    def __init__(self, typ=None, expr=None):
        self.typ = typ
        self.expr = expr


# ──────────────────────────────────────────────────────────────────────
# Parser
# ──────────────────────────────────────────────────────────────────────

TYPE_KW = {TOK_VOID, TOK_INT, TOK_CHAR, TOK_SHORT, TOK_LONG,
           TOK_UNSIGNED, TOK_SIGNED, TOK_STRUCT, TOK_ENUM}


class Parser:
    def __init__(self, tok):
        self.tok = tok
        self.strlits = []

    def _err(self, msg):
        t = self.tok.toks[0] if self.tok.toks else Token(TOK_EOF)
        raise SyntaxError(f'{self.tok.fn}:{t.line}:{t.col}: {msg}')

    def _peek(self):
        return self.tok.toks[0].kind if self.tok.toks else TOK_EOF

    def _adv(self):
        return self.tok.toks.pop(0) if self.tok.toks else Token(TOK_EOF)

    def _expect(self, k):
        t = self._adv()
        if t.kind != k:
            self._err(f'Expected {k}, got {t.kind}={t.val!r}')
        return t

    def _match(self, k):
        if self._peek() == k:
            return self._adv()
        return None

    # ── Types ──

    def _parse_type(self):
        parts = []
        ptr = 0
        while self._peek() in (TOK_CONST, TOK_VOLATILE, TOK_STATIC, TOK_EXTERN):
            self._adv()
        while self._peek() in (TOK_UNSIGNED, TOK_SIGNED):
            parts.append(self._adv().val)
            if self._peek() == TOK_INT:
                parts.append(self._adv().val)
        k = self._peek()
        if k == TOK_INT:
            parts.append(self._adv().val)
        elif k == TOK_CHAR:
            parts.append(self._adv().val)
        elif k == TOK_SHORT:
            self._adv()
            parts.append('short')
        elif k == TOK_LONG:
            self._adv()
            parts.append('long')
        elif k == TOK_VOID:
            parts.append(self._adv().val)
        elif k == TOK_STRUCT:
            self._adv()
            n = self._match(TOK_ID)
            if self._peek() == TOK_LBRACE:
                self._skip_block()
            parts.append(f'struct {n.val if n else ""}')
        elif k == TOK_ENUM:
            self._adv()
            self._match(TOK_ID)
            if self._peek() == TOK_LBRACE:
                self._skip_block()
            parts.append('int')
        else:
            if not parts:
                return None
        while self._peek() == TOK_STAR:
            ptr += 1
            self._adv()
        spec = ' '.join(parts)
        if spec in ('signed int',):
            spec = 'int'
        if spec in ('unsigned int',):
            spec = 'unsigned int'
        if spec in ('signed char',):
            spec = 'char'
        if spec in ('unsigned char',):
            spec = 'unsigned char'
        return Type(spec, ptr)

    def _skip_block(self):
        self._expect(TOK_LBRACE)
        d = 1
        while d > 0:
            k = self._peek()
            if k == TOK_LBRACE:
                d += 1
                self._adv()
            elif k == TOK_RBRACE:
                d -= 1
                self._adv()
            elif k == TOK_EOF:
                break
            else:
                self._adv()

    # ── Top-level ──

    def parse(self):
        prog = Program()
        while self._peek() != TOK_EOF:
            d = self._parse_tld()
            if d:
                prog.decls.append(d)
        return prog

    def _parse_tld(self):
        typ = self._parse_type()
        if typ is None:
            self._err('Expected declaration')
        name = self._expect(TOK_ID).val
        if self._peek() == TOK_LPAREN:
            self._adv()
            params = []
            if self._peek() != TOK_RPAREN:
                params = self._parse_params()
            self._expect(TOK_RPAREN)
            if self._peek() == TOK_LBRACE:
                return FunctionDecl(typ, name, params, self._parse_block())
            self._expect(TOK_SEMI)
            return None
        return self._parse_var_tail(typ, name, True)

    def _parse_params(self):
        pp = []
        while self._peek() != TOK_RPAREN:
            typ = self._parse_type()
            if typ is None:
                self._err('Expected param type')
            n = self._expect(TOK_ID).val if self._peek() == TOK_ID else ''
            if self._peek() == TOK_LBRACK:
                self._adv()
                if self._peek() == TOK_NUM:
                    self._adv()
                self._expect(TOK_RBRACK)
                typ = typ.with_ptr()
            pp.append(ParamDecl(typ, n))
            if self._peek() != TOK_RPAREN:
                self._expect(TOK_COMMA)
        return pp

    def _parse_var_tail(self, typ, name, is_global=False):
        init = None
        asize = 0
        if self._peek() == TOK_LBRACK:
            self._adv()
            if self._peek() == TOK_NUM:
                t = self._adv()
                asize = int(t.val, 0)
                if asize > 0:
                    typ = typ.with_ptr()
            self._expect(TOK_RBRACK)
        if self._peek() == TOK_ASSIGN:
            self._adv()
            init = self._parse_assign()
        self._expect(TOK_SEMI)
        d = VarDecl(typ, name, init, not is_global)
        d.is_global = is_global
        d.array_size = asize
        return d

    # ── Block ──

    def _parse_block(self):
        self._expect(TOK_LBRACE)
        b = Block()
        while self._peek() not in (TOK_RBRACE, TOK_EOF):
            s = self._parse_stmt()
            if s:
                b.stmts.append(s)
        self._expect(TOK_RBRACE)
        return b

    def _parse_stmt(self):
        k = self._peek()
        if k == TOK_IF:
            return self._parse_if()
        if k == TOK_WHILE:
            return self._parse_while()
        if k == TOK_FOR:
            return self._parse_for()
        if k == TOK_DO:
            return self._parse_do()
        if k == TOK_RETURN:
            return self._parse_ret()
        if k == TOK_BREAK:
            self._adv()
            self._expect(TOK_SEMI)
            return BreakStmt()
        if k == TOK_CONTINUE:
            self._adv()
            self._expect(TOK_SEMI)
            return ContinueStmt()
        if k == TOK_SWITCH:
            return self._parse_switch()
        if k == TOK_LBRACE:
            return self._parse_block()
        if k == TOK_CASE:
            self._err('case outside switch')
        if k == TOK_DEFAULT:
            self._err('default outside switch')
        if k in TYPE_KW:
            return self._parse_decl_stmt()
        if k == TOK_SEMI:
            self._adv()
            return ExprStmt(None)
        e = self._parse_expr()
        self._expect(TOK_SEMI)
        return ExprStmt(e)

    def _parse_decl_stmt(self):
        typ = self._parse_type()
        if typ is None:
            self._err('Expected type')
        name = self._expect(TOK_ID).val
        return self._parse_var_tail(typ, name)

    def _parse_if(self):
        self._adv()
        self._expect(TOK_LPAREN)
        c = self._parse_expr()
        self._expect(TOK_RPAREN)
        t = self._parse_stmt()
        e = None
        if self._peek() == TOK_ELSE:
            self._adv()
            e = self._parse_stmt()
        return IfStmt(c, t, e)

    def _parse_while(self):
        self._adv()
        self._expect(TOK_LPAREN)
        c = self._parse_expr()
        self._expect(TOK_RPAREN)
        return WhileStmt(c, self._parse_stmt())

    def _parse_do(self):
        self._adv()
        b = self._parse_stmt()
        self._expect(TOK_WHILE)
        self._expect(TOK_LPAREN)
        c = self._parse_expr()
        self._expect(TOK_RPAREN)
        self._expect(TOK_SEMI)
        return DoWhileStmt(b, c)

    def _parse_for(self):
        self._adv()
        self._expect(TOK_LPAREN)
        init = None
        if self._peek() in TYPE_KW:
            init = self._parse_decl_stmt()
        elif self._peek() != TOK_SEMI:
            init = self._parse_expr()
        self._expect(TOK_SEMI)
        cond = None
        if self._peek() != TOK_SEMI:
            cond = self._parse_expr()
        self._expect(TOK_SEMI)
        inc = None
        if self._peek() != TOK_RPAREN:
            inc = self._parse_expr()
        self._expect(TOK_RPAREN)
        return ForStmt(init, cond, inc, self._parse_stmt())

    def _parse_ret(self):
        self._adv()
        e = None
        if self._peek() != TOK_SEMI:
            e = self._parse_expr()
        self._expect(TOK_SEMI)
        return ReturnStmt(e)

    def _parse_switch(self):
        self._adv()
        self._expect(TOK_LPAREN)
        e = self._parse_expr()
        self._expect(TOK_RPAREN)
        self._expect(TOK_LBRACE)
        cases = []
        default = None
        while self._peek() not in (TOK_RBRACE, TOK_EOF):
            if self._peek() == TOK_CASE:
                self._adv()
                v = self._parse_expr()
                self._expect(TOK_COLON)
                ss = []
                while self._peek() not in (TOK_CASE, TOK_DEFAULT, TOK_RBRACE, TOK_EOF):
                    s = self._parse_stmt()
                    if s:
                        ss.append(s)
                cases.append((v, ss))
            elif self._peek() == TOK_DEFAULT:
                self._adv()
                self._expect(TOK_COLON)
                default = []
                while self._peek() not in (TOK_CASE, TOK_DEFAULT, TOK_RBRACE, TOK_EOF):
                    s = self._parse_stmt()
                    if s:
                        default.append(s)
            else:
                self._err('Expected case/default')
        self._expect(TOK_RBRACE)
        if not cases:
            if default:
                b = Block()
                b.stmts = default
                return b
            return ExprStmt(None)
        last = Block()
        if default:
            last.stmts = default
        for v, ss in reversed(cases):
            blk = Block()
            blk.stmts = ss
            cmp = BinaryOp(TOK_EQ, e, v)
            last = IfStmt(cmp, blk, last)
        return last

    # ── Expressions ──

    def _parse_expr(self):
        return self._parse_assign()

    def _parse_assign(self):
        e = self._parse_cond()
        aops = {TOK_ASSIGN: '=', TOK_ADD_ASSIGN: '+=', TOK_SUB_ASSIGN: '-=',
                TOK_MUL_ASSIGN: '*=', TOK_DIV_ASSIGN: '/=', TOK_MOD_ASSIGN: '%=',
                TOK_AND_ASSIGN: '&=', TOK_OR_ASSIGN: '|=',
                TOK_XOR_ASSIGN: '^=', TOK_LSH_ASSIGN: '<<=',
                TOK_RSH_ASSIGN: '>>='}
        k = self._peek()
        if k in aops:
            self._adv()
            return Assignment(aops[k], e, self._parse_assign())
        return e

    def _parse_cond(self):
        e = self._parse_lor()
        if self._peek() == TOK_QUES:
            self._adv()
            t = self._parse_expr()
            self._expect(TOK_COLON)
            f = self._parse_cond()
            return CondExpr(e, t, f)
        return e

    def _parse_lor(self):
        e = self._parse_land()
        while self._peek() == TOK_LOR:
            self._adv()
            e = BinaryOp(TOK_LOR, e, self._parse_land())
        return e

    def _parse_land(self):
        e = self._parse_bor()
        while self._peek() == TOK_LAND:
            self._adv()
            e = BinaryOp(TOK_LAND, e, self._parse_bor())
        return e

    def _parse_bor(self):
        e = self._parse_xor()
        while self._peek() == TOK_PIPE:
            self._adv()
            e = BinaryOp(TOK_PIPE, e, self._parse_xor())
        return e

    def _parse_xor(self):
        e = self._parse_band()
        while self._peek() == TOK_CARET:
            self._adv()
            e = BinaryOp(TOK_CARET, e, self._parse_band())
        return e

    def _parse_band(self):
        e = self._parse_eq()
        while self._peek() == TOK_AND:
            self._adv()
            e = BinaryOp(TOK_AND, e, self._parse_eq())
        return e

    def _parse_eq(self):
        e = self._parse_cmp()
        while self._peek() in (TOK_EQ, TOK_NE):
            op = self._adv().kind
            e = BinaryOp(op, e, self._parse_cmp())
        return e

    def _parse_cmp(self):
        e = self._parse_shift()
        while self._peek() in (TOK_LT, TOK_GT, TOK_LE, TOK_GE):
            op = self._adv().kind
            e = BinaryOp(op, e, self._parse_shift())
        return e

    def _parse_shift(self):
        e = self._parse_add()
        while self._peek() in (TOK_LSH, TOK_RSH):
            op = self._adv().kind
            e = BinaryOp(op, e, self._parse_add())
        return e

    def _parse_add(self):
        e = self._parse_mul()
        while self._peek() in (TOK_PLUS, TOK_MINUS):
            op = self._adv().kind
            e = BinaryOp(op, e, self._parse_mul())
        return e

    def _parse_mul(self):
        e = self._parse_cast()
        while self._peek() in (TOK_STAR, TOK_SLASH, TOK_PERCENT):
            op = self._adv().kind
            e = BinaryOp(op, e, self._parse_cast())
        return e

    def _parse_cast(self):
        if self._peek() == TOK_LPAREN:
            saved = list(self.tok.toks)
            self._adv()
            typ = self._parse_type()
            if typ and self._peek() == TOK_RPAREN:
                self._adv()
                return CastExpr(typ, self._parse_cast())
            self.tok.toks = saved
        return self._parse_unary()

    def _parse_unary(self):
        k = self._peek()
        if k == TOK_INC:
            self._adv()
            return UnaryOp('++pre', self._parse_unary())
        if k == TOK_DEC:
            self._adv()
            return UnaryOp('--pre', self._parse_unary())
        if k == TOK_AND:
            self._adv()
            return UnaryOp('&', self._parse_unary())
        if k == TOK_STAR:
            self._adv()
            return UnaryOp('*', self._parse_unary())
        if k == TOK_PLUS:
            self._adv()
            return self._parse_unary()
        if k == TOK_MINUS:
            self._adv()
            return UnaryOp('-', self._parse_unary())
        if k == TOK_TILDE:
            self._adv()
            return UnaryOp('~', self._parse_unary())
        if k == TOK_LNOT:
            self._adv()
            return UnaryOp('!', self._parse_unary())
        if k == TOK_SIZEOF:
            self._adv()
            if self._peek() == TOK_LPAREN:
                self._adv()
                saved = list(self.tok.toks)
                typ = self._parse_type()
                if typ and self._peek() == TOK_RPAREN:
                    self._adv()
                    return SizeofExpr(typ=typ)
                self.tok.toks = saved
                e = self._parse_expr()
                self._expect(TOK_RPAREN)
                return SizeofExpr(expr=e)
            return SizeofExpr(expr=self._parse_unary())
        return self._parse_post()

    def _parse_post(self):
        e = self._parse_prim()
        while True:
            k = self._peek()
            if k == TOK_LBRACK:
                self._adv()
                i = self._parse_expr()
                self._expect(TOK_RBRACK)
                e = ArraySub(e, i)
            elif k == TOK_LPAREN:
                self._adv()
                args = []
                if self._peek() != TOK_RPAREN:
                    args.append(self._parse_assign())
                    while self._peek() == TOK_COMMA:
                        self._adv()
                        args.append(self._parse_assign())
                self._expect(TOK_RPAREN)
                if isinstance(e, VarRef):
                    e = FuncCall(e.name, args)
                else:
                    e = FuncCall('(*fn)', [e] + args)
            elif k == TOK_DOT:
                self._adv()
                m = self._expect(TOK_ID).val
                e = BinaryOp(TOK_DOT, e, VarRef(m))
            elif k == TOK_ARROW:
                self._adv()
                m = self._expect(TOK_ID).val
                e = BinaryOp(TOK_ARROW, e, VarRef(m))
            elif k == TOK_INC:
                self._adv()
                e = UnaryOp('++post', e)
            elif k == TOK_DEC:
                self._adv()
                e = UnaryOp('--post', e)
            else:
                break
        return e

    def _parse_prim(self):
        k = self._peek()
        if k == TOK_NUM:
            t = self._adv()
            return Const(int(t.val, 0) & 0xFFFF)
        if k == TOK_CHR:
            t = self._adv()
            return Const(int(t.val) & 0xFFFF)
        if k == TOK_STR:
            t = self._adv()
            lbl = f'.Lstr{len(self.strlits)}'
            self.strlits.append((lbl, t.val))
            return StringLit(t.val, lbl)
        if k == TOK_ID:
            t = self._adv()
            return VarRef(t.val)
        if k == TOK_LPAREN:
            self._adv()
            e = self._parse_expr()
            self._expect(TOK_RPAREN)
            return e
        self._err(f'Unexpected token {k}')


# ──────────────────────────────────────────────────────────────────────
# Symbol Table
# ──────────────────────────────────────────────────────────────────────

class Sym:
    def __init__(self, parent=None):
        self.d = {}
        self.parent = parent

    def put(self, n, v):
        self.d[n] = v
        return True

    def get(self, n):
        if n in self.d:
            return self.d[n]
        if self.parent:
            return self.parent.get(n)
        return None

    def get_local(self, n):
        return self.d.get(n)


# ──────────────────────────────────────────────────────────────────────
# Code Generator -- optimized for code size
# ──────────────────────────────────────────────────────────────────────

class CGen:
    def __init__(self, prog, strlits):
        self.prog = prog
        self.strlits = strlits
        self.lines = []
        self.lc = 0
        self.globals = Sym()
        self.global_addrs = {}
        self.next_global = 0

    def L(self, pf='L'):
        self.lc += 1
        return f'.{pf}{self.lc}'

    def emit(self, s=''):
        self.lines.append(s)

    def emit_lbl(self, n):
        self.lines.append(f'{n}:')

    def _imm6(self, v):
        """Return signed 6-bit value if representable, else None."""
        v = v & 0xFFFF
        if v >= 0x8000:
            v2 = v - 0x10000
        else:
            v2 = v
        return v2 if -32 <= v2 <= 31 else None

    def _load_r1(self, v):
        v = v & 0xFFFF
        if v == 0:
            self.emit('    XOR R1, R0, R0')
        else:
            s = self._imm6(v)
            if s is not None:
                self.emit(f'    ADDI R1, R0, #{s}')
            else:
                self.emit(f'    LDI R1, #0x{v:04X}')

    # ── Pass 1: resolve ──

    def resolve(self):
        for d in self.prog.decls:
            if isinstance(d, VarDecl):
                d.is_global = True
                d.is_local = False
                self.globals.put(d.name, d)
                self.global_addrs[d.name] = self.next_global
                self.next_global += max(2, d.array_size * 2)
            elif isinstance(d, FunctionDecl):
                self.globals.put(d.name, d)

    def _const_val(self, e):
        """Evaluate constant expression at compile time."""
        if isinstance(e, Const):
            return e.v
        if isinstance(e, UnaryOp):
            v = self._const_val(e.expr)
            if v is None:
                return None
            if e.op == '-':
                return (-v) & 0xFFFF
            if e.op == '~':
                return (~v) & 0xFFFF
            if e.op == '!':
                return 0 if v else 1
            return None
        if isinstance(e, BinaryOp):
            l = self._const_val(e.left)
            r = self._const_val(e.right)
            if l is None or r is None:
                return None
            if e.op == TOK_PLUS:
                return (l + r) & 0xFFFF
            if e.op == TOK_MINUS:
                return (l - r) & 0xFFFF
            if e.op == TOK_STAR:
                return (l * r) & 0xFFFF
            if e.op == TOK_SLASH:
                return (l // r) & 0xFFFF if r else 0
            if e.op == TOK_AND:
                return l & r
            if e.op == TOK_PIPE:
                return l | r
            if e.op == TOK_CARET:
                return l ^ r
            if e.op == TOK_EQ:
                return 1 if l == r else 0
            if e.op == TOK_NE:
                return 1 if l != r else 0
            if e.op == TOK_LT:
                def s16(x):
                    return x if x < 0x8000 else x - 0x10000
                return 1 if s16(l) < s16(r) else 0
            if e.op == TOK_GT:
                def s16(x):
                    return x if x < 0x8000 else x - 0x10000
                return 1 if s16(l) > s16(r) else 0
            if e.op == TOK_LE:
                def s16(x):
                    return x if x < 0x8000 else x - 0x10000
                return 1 if s16(l) <= s16(r) else 0
            if e.op == TOK_GE:
                def s16(x):
                    return x if x < 0x8000 else x - 0x10000
                return 1 if s16(l) >= s16(r) else 0
            if e.op == TOK_LAND:
                return 1 if l and r else 0
            if e.op == TOK_LOR:
                return 1 if l or r else 0
            return None
        if isinstance(e, CastExpr):
            return self._const_val(e.expr)
        return None

    # ── Top-level generation ──

    def generate(self):
        self.resolve()
        self._emit_startup()
        self._emit_strlits()
        self._emit_data_init()
        for d in self.prog.decls:
            if isinstance(d, FunctionDecl) and d.body:
                self._gen_func(d)
        self._emit_runtime()
        return '\n'.join(self.lines)

    def _emit_strlits(self):
        for lbl, val in self.strlits:
            self.emit_lbl(lbl)
            for i in range(0, len(val) + 1, 2):
                w = ord(val[i]) if i < len(val) else 0
                if i + 1 < len(val):
                    w |= ord(val[i + 1]) << 8
                self.emit(f'    .word 0x{w:04X}')

    def _emit_data_init(self):
        """Store global variable initializers in ROM."""
        self.emit_lbl('__data_init')
        for d in self.prog.decls:
            if isinstance(d, VarDecl) and d.init:
                cv = self._const_val(d.init)
                if cv is not None:
                    self.emit(f'    .word 0x{cv:04X}')
                else:
                    self.emit('    .word 0x0000')
            elif isinstance(d, VarDecl):
                self.emit('    .word 0x0000')
        self.emit_lbl('__data_init_end')

    def _emit_startup(self):
        gsz = self.next_global
        self.emit('; C runtime startup')
        self.emit('    LDI R7, #0x1FFA')
        if gsz > 0:
            lp = self.L('init')
            self.emit('    LDI R1, #__data_init')
            self.emit('    LDI R2, #0')
            self.emit_lbl(lp)
            self.emit('    LD R3, [R1 + 0]')
            self.emit('    ST R3, [R2 + 0]')
            self.emit('    ADDI R1, R1, #2')
            self.emit('    ADDI R2, R2, #2')
            self.emit('    LDI R4, #__data_init_end')
            self.emit(f'    BLT R1, R4, {lp}')
        self.emit('    LDI R5, #_exit')
        self.emit('    LDI R1, #main')
        self.emit('    JMP R1')
        self.emit_lbl('_exit')
        self.emit('    JMP R5')
        self.emit()
        self.emit()

    # ── Function codegen ──

    def _gen_func(self, f):
        locals = []
        self._collect_locals(f.body, locals)
        n_local = len(locals)
        n_param = len(f.params)
        n_reg_param = min(n_param, 3)

        # Local offsets: R6 - 2, R6 - 4, ...
        for i, (n, d) in enumerate(locals):
            d.off = -2 - i * 2

        # Register param offsets: below locals
        # R6 - 2 - n_local*2 (arg1), R6 - 4 - n_local*2 (arg2), ...
        for i, p in enumerate(f.params):
            if i < 3:
                p.off = -2 - n_local * 2 - i * 2
            else:
                p.off = 4 + i * 2
            # Register parameter in symbol table
            pd = VarDecl(p.typ, p.name, is_local=True)
            pd.is_param = True
            pd.off = p.off
            self.globals.put(p.name, pd)

        # Register locals in symbol table
        for n, d in locals:
            self.globals.put(n, d)

        n_total = n_local + n_reg_param

        self.emit(f';--- {f.name}(...)')
        self.emit_lbl(f.name)
        self.emit('    ST R5, [R7]')
        self.emit('    ADDI R7, R7, #-2')
        self.emit('    ST R6, [R7]')
        self.emit('    ADDI R7, R7, #-2')
        self.emit('    ADD R6, R7, R0')
        self.emit('    ADDI R6, R6, #2')
        if n_total > 0:
            self.emit(f'    ADDI R7, R7, #{-n_total * 2}')
        for i, p in enumerate(f.params):
            if i < 3:
                self.emit(f'    ST R{i+2}, [R6 {p.off:+d}]')
        self.emit()

        self._gen_block(f.body, f.name, None, None)

        self.emit_lbl(f'{f.name}_epi')
        if n_total > 0:
            self.emit(f'    ADDI R7, R7, #{n_total * 2}')
        self.emit('    ADDI R7, R7, #2')
        self.emit('    LD R6, [R7]')
        self.emit('    ADDI R7, R7, #2')
        self.emit('    LD R5, [R7]')
        self.emit('    JMP R5')
        self.emit()

    def _collect_locals(self, block, out):
        for s in block.stmts:
            if isinstance(s, VarDecl):
                s.is_local = True
                s.is_global = False
                out.append((s.name, s))
            elif isinstance(s, Block):
                self._collect_locals(s, out)
            elif isinstance(s, IfStmt):
                if isinstance(s.then, Block):
                    self._collect_locals(s.then, out)
                if isinstance(s.els, Block):
                    self._collect_locals(s.els, out)
            elif isinstance(s, WhileStmt):
                if isinstance(s.body, Block):
                    self._collect_locals(s.body, out)
            elif isinstance(s, ForStmt):
                if isinstance(s.init, VarDecl):
                    s.init.is_local = True
                    s.init.is_global = False
                    out.append((s.init.name, s.init))
                if isinstance(s.body, Block):
                    self._collect_locals(s.body, out)

    def _var_info(self, name):
        d = self.globals.get(name)
        if d is None:
            return None
        if d.is_global:
            return ('g', self.global_addrs.get(name, 0))
        if d.is_local or d.is_param:
            return ('l', d.off)
        return None

    # ── Block / Stmt ──

    def _gen_block(self, blk, fn, bl, cl):
        for s in blk.stmts:
            self._gen_stmt(s, fn, bl, cl)

    def _gen_stmt(self, s, fn, bl, cl):
        if isinstance(s, VarDecl):
            if s.init:
                self._gen_expr(s.init)
                vi = self._var_info(s.name)
                if vi:
                    t, o = vi
                    base = 'R6' if t == 'l' else 'R0'
                    self.emit(f'    ST R1, [{base} {o:+d}]')

        elif isinstance(s, ExprStmt):
            if s.expr:
                self._gen_expr(s.expr)

        elif isinstance(s, ReturnStmt):
            if s.expr:
                self._gen_expr(s.expr)
            else:
                self.emit('    XOR R1, R0, R0')
            self.emit(f'    JMP {fn}_epi')

        elif isinstance(s, IfStmt):
            self._gen_if(s, fn, bl, cl)

        elif isinstance(s, WhileStmt):
            start = self.L('w')
            end = self.L('we')
            self.emit_lbl(start)
            self._gen_cond(s.cond, end, True)
            self._gen_stmt(s.body, fn, end, start)
            self.emit(f'    JMP {start}')
            self.emit_lbl(end)

        elif isinstance(s, ForStmt):
            check = self.L('fc')
            inc = self.L('fi')
            end = self.L('fe')
            if s.init:
                self._gen_stmt(s.init, fn, None, None)
            self.emit_lbl(check)
            if s.cond:
                self._gen_cond(s.cond, end, True)
            self._gen_stmt(s.body, fn, end, inc)
            self.emit_lbl(inc)
            if s.inc:
                self._gen_expr(s.inc)
            self.emit(f'    JMP {check}')
            self.emit_lbl(end)

        elif isinstance(s, DoWhileStmt):
            start = self.L('d')
            end = self.L('de')
            self.emit_lbl(start)
            self._gen_stmt(s.body, fn, end, start)
            self._gen_cond(s.cond, start, False)
            self.emit_lbl(end)

        elif isinstance(s, BreakStmt):
            if bl:
                self.emit(f'    JMP {bl}')

        elif isinstance(s, ContinueStmt):
            if cl:
                self.emit(f'    JMP {cl}')

        elif isinstance(s, Block):
            self._gen_block(s, fn, bl, cl)

    def _gen_if(self, s, fn, bl, cl):
        e = self.L('el')
        end = self.L('ei')
        self._gen_cond(s.cond, e, True)
        self._gen_stmt(s.then, fn, bl, cl)
        if s.els:
            self.emit(f'    JMP {end}')
        self.emit_lbl(e)
        if s.els:
            self._gen_stmt(s.els, fn, bl, cl)
            self.emit_lbl(end)

    def _gen_cond(self, cond, target, invert):
        """Jump to target when condition is false (invert=True) or true (invert=False)."""
        cv = self._const_val(cond)
        if cv is not None:
            if (cv and not invert) or (not cv and invert):
                pass
            else:
                self.emit(f'    JMP {target}')
            return

        if isinstance(cond, BinaryOp):
            op = cond.op
            if op == TOK_LAND:
                if invert:
                    m = self.L('la')
                    self._gen_cond(cond.left, m, True)
                    self._gen_cond(cond.right, target, True)
                    self.emit_lbl(m)
                else:
                    m = self.L('la')
                    self._gen_cond(cond.left, m, True)
                    self._gen_cond(cond.right, m, True)
                    self.emit(f'    JMP {target}')
                    self.emit_lbl(m)
                return
            if op == TOK_LOR:
                if invert:
                    m = self.L('lo')
                    self._gen_cond(cond.left, m, False)
                    self._gen_cond(cond.right, target, False)
                    self.emit_lbl(m)
                else:
                    m = self.L('lo')
                    self._gen_cond(cond.left, m, False)
                    self._gen_cond(cond.right, m, False)
                    self.emit(f'    JMP {target}')
                    self.emit_lbl(m)
                return

            # Binary comparison
            self._gen_expr(cond.left)
            self.emit('    ST R1, [R7]')
            self.emit('    ADDI R7, R7, #-2')
            self._gen_expr(cond.right)
            self.emit('    ADDI R7, R7, #2')
            self.emit('    LD R2, [R7]')
            # R2 = left, R1 = right

            if op in (TOK_EQ, TOK_NE):
                self.emit('    XOR R1, R2, R1')
                if op == TOK_EQ:
                    self.emit(f'    {"BNE" if invert else "BEQ"} R1, R0, {target}')
                else:
                    self.emit(f'    {"BEQ" if invert else "BNE"} R1, R0, {target}')
                return

            if op == TOK_LT:
                # BLT R2, R1 = BLT left, right → true when left < right
                if not invert:
                    self.emit(f'    BLT R2, R1, {target}')
                else:
                    m = self.L('cl')
                    self.emit(f'    BLT R2, R1, {m}')  # skip if left < right (true)
                    self.emit(f'    JMP {target}')      # left >= right: go to target
                    self.emit_lbl(m)
            elif op == TOK_GT:
                # BLT R1, R2 = BLT right, left → true when left > right
                if not invert:
                    self.emit(f'    BLT R1, R2, {target}')
                else:
                    m = self.L('cl')
                    self.emit(f'    BLT R1, R2, {m}')  # skip if left > right (true)
                    self.emit(f'    JMP {target}')      # left <= right: go to target
                    self.emit_lbl(m)
            elif op == TOK_LE:
                # BLT R1, R2 = BLT right, left → true when left > right (false for LE)
                if not invert:
                    m = self.L('cl')
                    self.emit(f'    BLT R1, R2, {m}')  # skip if left > right (false)
                    self.emit(f'    JMP {target}')      # left <= right: go to target
                    self.emit_lbl(m)
                else:
                    self.emit(f'    BLT R1, R2, {target}')  # jump when left > right (false)
            elif op == TOK_GE:
                # BLT R2, R1 = BLT left, right → true when left < right (false for GE)
                if not invert:
                    m = self.L('cg')
                    self.emit(f'    BLT R2, R1, {m}')  # skip if left < right (false)
                    self.emit(f'    JMP {target}')      # left >= right: go to target
                    self.emit_lbl(m)
                else:
                    self.emit(f'    BLT R2, R1, {target}')  # jump when left < right (false)
                if invert:
                    self.emit(f'    JMP {target}')
                else:
                    self.emit(f'    JMP {m}')
                self.emit_lbl(m)
                return
            return

        self._gen_expr(cond)
        if invert:
            self.emit(f'    BEQ R1, R0, {target}')
        else:
            self.emit(f'    BNE R1, R0, {target}')

    # ── Expression codegen ──

    def _gen_expr(self, e):
        cv = self._const_val(e)
        if cv is not None:
            self._load_r1(cv)
            return

        if isinstance(e, Const):
            self._load_r1(e.v)

        elif isinstance(e, VarRef):
            vi = self._var_info(e.name)
            if vi:
                t, o = vi
                base = 'R6' if t == 'l' else 'R0'
                self.emit(f'    LD R1, [{base} {o:+d}]')

        elif isinstance(e, BinaryOp):
            self._gen_binop(e)

        elif isinstance(e, UnaryOp):
            self._gen_unop(e)

        elif isinstance(e, Assignment):
            self._gen_assign(e)

        elif isinstance(e, FuncCall):
            self._gen_call(e)

        elif isinstance(e, ArraySub):
            self._gen_expr(e.arr)
            self.emit('    ST R1, [R7]')
            self.emit('    ADDI R7, R7, #-2')
            self._gen_expr(e.idx)
            self.emit('    ADDI R3, R0, #1')
            self.emit('    SLL R1, R1, R3')
            self.emit('    ADDI R7, R7, #2')
            self.emit('    LD R2, [R7]')
            self.emit('    ADD R1, R2, R1')
            self.emit('    LD R1, [R1 + 0]')

        elif isinstance(e, CastExpr):
            self._gen_expr(e.expr)

        elif isinstance(e, StringLit):
            self.emit(f'    LDI R1, #{e.lbl}')

        elif isinstance(e, SizeofExpr):
            if e.typ:
                sz = 2 if e.typ.is_ptr() or e.typ.spec != 'void' else 0
            else:
                sz = 2
            self._load_r1(sz)

        elif isinstance(e, CondExpr):
            el = self.L('ce')
            en = self.L('cn')
            self._gen_cond(e.cond, el, True)
            self._gen_expr(e.t)
            self.emit(f'    JMP {en}')
            self.emit_lbl(el)
            self._gen_expr(e.e)
            self.emit_lbl(en)

    def _gen_binop(self, e):
        op = e.op
        if op == TOK_COMMA:
            self._gen_expr(e.left)
            self._gen_expr(e.right)
            return

        if op in (TOK_LAND, TOK_LOR):
            end = self.L('bo')
            self._gen_expr(e.left)
            if op == TOK_LAND:
                self.emit(f'    BEQ R1, R0, {end}')
            else:
                self.emit(f'    BNE R1, R0, {end}')
            self._gen_expr(e.right)
            self.emit_lbl(end)
            return

        # Optimize: if right is a small constant, use immediate
        cv = self._const_val(e.right)
        if cv is not None:
            self._gen_expr(e.left)
            s = self._imm6(cv)
            if s is not None:
                if op == TOK_PLUS:
                    self.emit(f'    ADDI R1, R1, #{s}')
                    return
                if op == TOK_MINUS and s == -cv:
                    self.emit(f'    ADDI R1, R1, #{-s}')
                    return
                if op == TOK_AND and cv == 1:
                    self.emit('    ADDI R2, R0, #1')
                    self.emit('    AND R1, R1, R2')
                    return
            # Load const into R2 for non-immediate ops
            self.emit('    ST R1, [R7]')
            self.emit('    ADDI R7, R7, #-2')
            self._load_r1(cv)
            self.emit('    ADDI R7, R7, #2')
            self.emit('    LD R2, [R7]')
            self._emit_binop_op(op)
            return

        # General case
        self._gen_expr(e.left)
        self.emit('    ST R1, [R7]')
        self.emit('    ADDI R7, R7, #-2')
        self._gen_expr(e.right)
        self.emit('    ADDI R7, R7, #2')
        self.emit('    LD R2, [R7]')
        self._emit_binop_op(op)

    def _emit_binop_op(self, op):
        alu = {TOK_PLUS: 'ADD', TOK_MINUS: 'SUB',
               TOK_AND: 'AND', TOK_PIPE: 'OR', TOK_CARET: 'XOR',
               TOK_LSH: 'SLL', TOK_RSH: 'SRL'}
        if op in alu:
            self.emit(f'    {alu[op]} R1, R2, R1')
        elif op in (TOK_EQ, TOK_NE, TOK_LT, TOK_GT, TOK_LE, TOK_GE):
            self._gen_cmp(op)
        elif op in (TOK_STAR, TOK_SLASH, TOK_PERCENT):
            self._gen_muldiv(op)

    def _gen_cmp(self, op):
        t = self.L('ct')
        e = self.L('ce')
        self.emit('    XOR R1, R0, R0')  # default false
        if op == TOK_EQ:
            self.emit('    XOR R3, R2, R1')
            self.emit(f'    BEQ R3, R0, {t}')
        elif op == TOK_NE:
            self.emit('    XOR R3, R2, R1')
            self.emit(f'    BNE R3, R0, {t}')
        elif op == TOK_LT:
            self.emit(f'    BLT R2, R1, {t}')
        elif op == TOK_GT:
            self.emit(f'    BLT R1, R2, {t}')
        elif op == TOK_LE:
            self.emit(f'    BLT R1, R2, {t}')
            self.emit(f'    JMP {e}')
            self.emit_lbl(t)
            self.emit('    ADDI R1, R0, #1')
            self.emit_lbl(e)
            return
        elif op == TOK_GE:
            self.emit(f'    BLT R2, R1, {t}')
            self.emit(f'    JMP {e}')
            self.emit_lbl(t)
            self.emit('    ADDI R1, R0, #1')
            self.emit_lbl(e)
            return
        self.emit(f'    JMP {e}')
        self.emit_lbl(t)
        self.emit('    ADDI R1, R0, #1')
        self.emit_lbl(e)

    def _gen_muldiv(self, op):
        name = {TOK_STAR: 'mul16', TOK_SLASH: 'div16', TOK_PERCENT: 'mod16'}[op]
        ret = self.L(f'{name}_ret')
        self.emit('    ST R5, [R7]')
        self.emit('    ADDI R7, R7, #-2')
        self.emit('    ST R1, [R7]')
        self.emit('    ADDI R7, R7, #-2')
        self.emit(f'    LDI R5, #{ret}')
        self.emit(f'    LDI R4, #__{name}')
        self.emit('    LD R1, [R7 +2]')
        self.emit(f'    JMP R4')
        self.emit_lbl(ret)
        self.emit('    ADDI R7, R7, #2')
        self.emit('    ADDI R7, R7, #2')
        self.emit('    LD R5, [R7]')

    def _gen_unop(self, e):
        op = e.op
        if op == '&':
            if isinstance(e.expr, VarRef):
                vi = self._var_info(e.expr.name)
                if vi:
                    t, o = vi
                    if t == 'l':
                        self.emit(f'    ADDI R1, R6, #{o}')
                    else:
                        self._load_r1(o)
            elif isinstance(e.expr, ArraySub):
                self._gen_expr(e.expr.arr)
                self.emit('    ST R1, [R7]')
                self.emit('    ADDI R7, R7, #-2')
                self._gen_expr(e.expr.idx)
                self.emit('    ADDI R3, R0, #1')
                self.emit('    SLL R1, R1, R3')
                self.emit('    ADDI R7, R7, #2')
                self.emit('    LD R2, [R7]')
                self.emit('    ADD R1, R2, R1')
            else:
                self._gen_expr(e.expr)
        elif op == '*':
            self._gen_expr(e.expr)
            self.emit('    LD R1, [R1 + 0]')
        elif op == '-':
            self._gen_expr(e.expr)
            self.emit('    SUB R1, R0, R1')
        elif op == '~':
            self._gen_expr(e.expr)
            self.emit('    XORI R1, R1, #-1')
        elif op == '!':
            self._gen_expr(e.expr)
            t = self.L('nt')
            en = self.L('ne')
            self.emit(f'    BEQ R1, R0, {t}')
            self.emit('    XOR R1, R0, R0')
            self.emit(f'    JMP {en}')
            self.emit_lbl(t)
            self.emit('    ADDI R1, R0, #1')
            self.emit_lbl(en)
        elif op in ('++pre', '--pre'):
            delta = 1 if op == '++pre' else -1
            self._gen_expr(e.expr)
            self.emit(f'    ADDI R1, R1, #{delta}')
            if isinstance(e.expr, VarRef):
                vi = self._var_info(e.expr.name)
                if vi:
                    t, o = vi
                    base = 'R6' if t == 'l' else 'R0'
                    self.emit(f'    ST R1, [{base} {o:+d}]')
        elif op in ('++post', '--post'):
            delta = 1 if op == '++post' else -1
            if isinstance(e.expr, VarRef):
                vi = self._var_info(e.expr.name)
                if vi:
                    t, o = vi
                    base = 'R6' if t == 'l' else 'R0'
                    self.emit(f'    LD R1, [{base} {o:+d}]')
                    self.emit(f'    ADDI R2, R1, #{delta}')
                    self.emit(f'    ST R2, [{base} {o:+d}]')
        else:
            self._gen_expr(e.expr)

    def _gen_assign(self, e):
        op = e.op
        if op != '=':
            mapping = {'+=': TOK_PLUS, '-=': TOK_MINUS, '*=': TOK_STAR,
                       '/=': TOK_SLASH, '%=': TOK_PERCENT, '&=': TOK_AND,
                       '|=': TOK_PIPE, '^=': TOK_CARET}
            if op in mapping and isinstance(e.lhs, VarRef):
                vi = self._var_info(e.lhs.name)
                if vi:
                    bo = mapping[op]
                    t, o = vi
                    base = 'R6' if t == 'l' else 'R0'
                    self.emit(f'    LD R1, [{base} {o:+d}]')
                    self.emit('    ST R1, [R7]')
                    self.emit('    ADDI R7, R7, #-2')
                    self._gen_expr(e.rhs)
                    self.emit('    ADDI R7, R7, #2')
                    self.emit('    LD R2, [R7]')
                    alu = {TOK_PLUS: 'ADD', TOK_MINUS: 'SUB', TOK_AND: 'AND',
                           TOK_PIPE: 'OR', TOK_CARET: 'XOR'}
                    if bo in alu:
                        self.emit(f'    {alu[bo]} R1, R2, R1')
                    self.emit(f'    ST R1, [{base} {o:+d}]')
                return
            return

        # Simple =
        if isinstance(e.lhs, VarRef):
            self._gen_expr(e.rhs)
            vi = self._var_info(e.lhs.name)
            if vi:
                t, o = vi
                base = 'R6' if t == 'l' else 'R0'
                self.emit(f'    ST R1, [{base} {o:+d}]')
        elif isinstance(e.lhs, ArraySub):
            self._gen_expr(e.lhs.arr)
            self.emit('    ST R1, [R7]')
            self.emit('    ADDI R7, R7, #-2')
            self._gen_expr(e.lhs.idx)
            self.emit('    ADDI R3, R0, #1')
            self.emit('    SLL R1, R1, R3')
            self.emit('    ADDI R7, R7, #2')
            self.emit('    LD R2, [R7]')
            self.emit('    ADD R1, R2, R1')
            self.emit('    ST R1, [R7]')
            self.emit('    ADDI R7, R7, #-2')
            self._gen_expr(e.rhs)
            self.emit('    ADDI R7, R7, #2')
            self.emit('    LD R2, [R7]')
            self.emit('    ST R1, [R2 + 0]')
        elif isinstance(e.lhs, UnaryOp) and e.lhs.op == '*':
            self._gen_expr(e.lhs.expr)
            self.emit('    ST R1, [R7]')
            self.emit('    ADDI R7, R7, #-2')
            self._gen_expr(e.rhs)
            self.emit('    ADDI R7, R7, #2')
            self.emit('    LD R2, [R7]')
            self.emit('    ST R1, [R2 + 0]')

    def _gen_call(self, e):
        n = len(e.args)
        for a in reversed(e.args):
            self._gen_expr(a)
            self.emit('    ST R1, [R7]')
            self.emit('    ADDI R7, R7, #-2')
        for i in range(min(n, 3)):
            self.emit('    ADDI R7, R7, #2')
            self.emit(f'    LD R{i+2}, [R7]')
        ret = self.L('cr')
        self.emit(f'    LDI R5, #{ret}')
        self.emit(f'    LDI R1, #{e.name}')
        self.emit(f'    JMP R1')
        self.emit_lbl(ret)
        if n > 3:
            self.emit(f'    ADDI R7, R7, #{(n - 3) * 2}')

    # ── Runtime routines ──

    def _needs_runtime(self):
        """Check if any binary op uses mul/div/mod."""
        def walk(n):
            if isinstance(n, BinaryOp) and n.op in (TOK_STAR, TOK_SLASH, TOK_PERCENT):
                yield n.op
            elif isinstance(n, UnaryOp):
                yield from walk(n.expr)
            elif isinstance(n, Assignment):
                yield from walk(n.rhs)
                if isinstance(n.lhs, Node):
                    yield from walk(n.lhs)
            elif isinstance(n, FuncCall):
                for a in n.args:
                    yield from walk(a)
            elif isinstance(n, ReturnStmt) and n.expr:
                yield from walk(n.expr)
            elif isinstance(n, IfStmt):
                yield from walk(n.cond)
                if isinstance(n.then, Node):
                    yield from walk(n.then)
                if n.els and isinstance(n.els, Node):
                    yield from walk(n.els)
            elif isinstance(n, WhileStmt):
                yield from walk(n.cond)
                if isinstance(n.body, Node):
                    yield from walk(n.body)
            elif isinstance(n, ForStmt):
                if n.cond:
                    yield from walk(n.cond)
                if n.inc:
                    yield from walk(n.inc)
                if isinstance(n.body, Node):
                    yield from walk(n.body)
            elif isinstance(n, VarDecl) and n.init:
                yield from walk(n.init)
            elif isinstance(n, Block):
                for s in n.stmts:
                    yield from walk(s)
            elif isinstance(n, ExprStmt) and n.expr:
                yield from walk(n.expr)
        for d in self.prog.decls:
            if isinstance(d, FunctionDecl) and d.body:
                for op in walk(d.body):
                    yield op

    def _emit_runtime(self):
        rts = set(self._needs_runtime())
        if not rts:
            return
        self.emit()
        self.emit('; ===== Runtime Library =====')
        self.emit()

        if TOK_STAR in rts:
            self.emit('__mul16:')
            self.emit('    XOR R3, R3, R3')
            self.emit('    ADDI R4, R0, #16')
            self.emit('__mul_lp:')
            self.emit('    ADDI R6, R0, #1')
            self.emit('    AND R6, R1, R6')
            self.emit('    BEQ R6, R0, __mul_sk')
            self.emit('    ADD R3, R3, R2')
            self.emit('__mul_sk:')
            self.emit('    ADDI R6, R0, #1')
            self.emit('    SLL R2, R2, R6')
            self.emit('    SRL R1, R1, R6')
            self.emit('    ADDI R4, R4, #-1')
            self.emit('    BNE R4, R0, __mul_lp')
            self.emit('    ADD R1, R0, R3')
            self.emit('    JMP R5')
            self.emit()

        if TOK_SLASH in rts:
            self.emit('__div16:')
            self.emit('    BEQ R1, R0, __div_exit')
            self.emit('    ST R5, [R7]')
            self.emit('    ADDI R7, R7, #-2')
            self.emit('    XOR R3, R3, R3')
            self.emit('    ADDI R4, R0, #16')
            self.emit('    XOR R5, R0, R5')
            self.emit('__div_lp:')
            self.emit('    ADDI R6, R0, #1')
            self.emit('    SLL R5, R5, R6')
            self.emit('    SLL R3, R3, R6')
            self.emit('    LDI R6, #0x8000')
            self.emit('    AND R6, R2, R6')
            self.emit('    BEQ R6, R0, __div_nb')
            self.emit('    ADDI R6, R0, #1')
            self.emit('    OR R5, R5, R6')
            self.emit('__div_nb:')
            self.emit('    ADDI R6, R0, #1')
            self.emit('    SLL R2, R2, R6')
            self.emit('    SUB R6, R5, R1')
            self.emit('    BLT R5, R1, __div_sk')
            self.emit('    ADD R5, R0, R6')
            self.emit('    ADDI R6, R0, #1')
            self.emit('    OR R3, R3, R6')
            self.emit('__div_sk:')
            self.emit('    ADDI R4, R4, #-1')
            self.emit('    BNE R4, R0, __div_lp')
            self.emit('    ADD R1, R0, R3')
            self.emit('    ADDI R7, R7, #2')
            self.emit('    LD R5, [R7]')
            self.emit('    JMP R5')
            self.emit()

        if TOK_PERCENT in rts:
            self.emit('__mod16:')
            self.emit('    BEQ R1, R0, __div_exit')
            self.emit('    ST R5, [R7]')
            self.emit('    ADDI R7, R7, #-2')
            self.emit('    XOR R3, R3, R3')
            self.emit('    ADDI R4, R0, #16')
            self.emit('    XOR R5, R0, R5')
            self.emit('__mod_lp:')
            self.emit('    ADDI R6, R0, #1')
            self.emit('    SLL R5, R5, R6')
            self.emit('    LDI R6, #0x8000')
            self.emit('    AND R6, R2, R6')
            self.emit('    BEQ R6, R0, __mod_nb')
            self.emit('    ADDI R6, R0, #1')
            self.emit('    OR R5, R5, R6')
            self.emit('__mod_nb:')
            self.emit('    ADDI R6, R0, #1')
            self.emit('    SLL R2, R2, R6')
            self.emit('    SUB R6, R5, R1')
            self.emit('    BLT R5, R1, __mod_sk')
            self.emit('    ADD R5, R0, R6')
            self.emit('__mod_sk:')
            self.emit('    ADDI R4, R4, #-1')
            self.emit('    BNE R4, R0, __mod_lp')
            self.emit('    ADD R1, R0, R5')
            self.emit('    ADDI R7, R7, #2')
            self.emit('    LD R5, [R7]')
            self.emit('    JMP R5')
            self.emit()

        if TOK_SLASH in rts or TOK_PERCENT in rts:
            self.emit('__div_exit:')
            self.emit('    XOR R1, R0, R0')
            self.emit('    JMP R5')


# ──────────────────────────────────────────────────────────────────────
# Peephole Optimizer
# ──────────────────────────────────────────────────────────────────────

def peephole(asm):
    lines = asm.split('\n')
    out = []
    i = 0
    while i < len(lines):
        line = lines[i]
        s = line.strip()
        if s == ';':
            i += 1
            continue
        # JMP x; JMP y -> JMP x
        if i + 1 < len(lines):
            ns = lines[i+1].strip()
            if s.startswith('JMP ') and ns.startswith('JMP '):
                i += 1
                continue
            # JMP x; x: -> remove JMP
            if s.startswith('JMP ') and ns.endswith(':'):
                tgt = s[4:].strip()
                lbl = ns[:-1].strip()
                if tgt == lbl:
                    i += 1
                    continue
            # ADDI Rx, Rx, #0 -> remove
            if s.startswith('ADDI R') and ', #0' in s:
                parts = s.split(',')
                if len(parts) >= 2:
                    r1 = s.split('ADDI ')[1].split(',')[0].strip()
                    if f'ADDI {r1}, {r1}' in s:
                        i += 1
                        continue
            # XORI Rx, Rx, #0 -> remove
            if s.startswith('XORI R') and ', #0' in s:
                parts = s.split(',')
                if len(parts) >= 2:
                    r1 = s.split('XORI ')[1].split(',')[0].strip()
                    if f'XORI {r1}, {r1}' in s:
                        i += 1
                        continue
            # ADDI R7, #-2; ADDI R7, #2 -> cancel
            if s == '    ADDI R7, R7, #-2' and ns == '    ADDI R7, R7, #2':
                i += 2
                continue
            if s == '    ADDI R7, R7, #2' and ns == '    ADDI R7, R7, #-2':
                i += 2
                continue
        out.append(line)
        i += 1
    return '\n'.join(out)


# ──────────────────────────────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────────────────────────────

def compile_c(source, filename, out_asm, out_hex, do_asm=True):
    try:
        tok = Tokenizer(source, filename)
        par = Parser(tok)
        prog = par.parse()
    except SyntaxError as e:
        print(f'ERROR: {e}', file=sys.stderr)
        return False
    try:
        cg = CGen(prog, par.strlits)
        asm = cg.generate()
        asm = peephole(asm)
    except RuntimeError as e:
        print(f'ERROR: {e}', file=sys.stderr)
        return False
    with open(out_asm, 'w') as f:
        f.write(asm)
    if do_asm:
        r = subprocess.run(
            [sys.executable,
             os.path.join(os.path.dirname(os.path.abspath(__file__)), 'asm.py'),
             out_asm, '-o', out_hex],
            capture_output=True, text=True)
        if r.returncode != 0:
            print(f'Assembly: {r.stderr}', file=sys.stderr)
            return False
    return True


def main():
    ap = argparse.ArgumentParser(description='C99 compiler for 16-bit RISC')
    ap.add_argument('input', help='C source file')
    ap.add_argument('-o', '--output', help='output .asm file')
    ap.add_argument('--hex', help='output .hex file')
    ap.add_argument('--no-asm', action='store_true', help='only generate .asm')
    args = ap.parse_args()
    fn = args.input
    out_asm = args.output or os.path.splitext(fn)[0] + '.asm'
    out_hex = args.hex or os.path.splitext(fn)[0] + '.hex'
    with open(fn) as f:
        src = f.read()
    ok = compile_c(src, fn, out_asm, out_hex, not args.no_asm)
    sys.exit(0 if ok else 1)


if __name__ == '__main__':
    main()
