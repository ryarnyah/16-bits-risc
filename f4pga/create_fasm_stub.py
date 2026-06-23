#!/usr/bin/env python3
import sys, os

stub_dir = sys.argv[1]
init_py = os.path.join(stub_dir, 'fasm', '__init__.py')

src = """from __future__ import print_function
import os, re
from .model import ValueFormat, SetFasmFeature, Annotation, FasmLine

def _pl(t):
    t = t.strip()
    if not t or t.startswith('#'):
        return None
    c = None
    if '#' in t:
        i = t.index('#')
        c = t[i+1:].strip()
        t = t[:i].strip()
    if not t:
        return None
    ann = None
    m = re.search(r'\{([^}]*)\}', t)
    if m:
        ann = []
        for p in m.group(1).split(','):
            p = p.strip()
            if '=' in p:
                k, v = p.split('=', 1)
                ann.append(Annotation(name=k.strip(), value=v.strip().strip('"')))
        t = t[:m.start()].strip() + t[m.end():].strip()
    sf = None
    if '=' in t:
        f, v = t.split('=', 1)
        f = f.strip()
        v = v.strip()
    else:
        f = t.strip()
        v = None
    ft = f
    s = None
    e = None
    m = re.search(r'\[(\d+)(?::(\d+))?\]$', ft)
    if m:
        if m.group(2) is not None:
            e = int(m.group(1))
            s = int(m.group(2))
        else:
            s = int(m.group(1))
        ft = ft[:m.start()]
    vl = 1
    vf = None
    if v:
        m = re.match(r"(\d+)'([bhod])([0-9a-fA-F_]+)", v)
        if m:
            fm = {'b': ValueFormat.VERILOG_BINARY, 'h': ValueFormat.VERILOG_HEX,
                  'o': ValueFormat.VERILOG_OCTAL, 'd': ValueFormat.VERILOG_DECIMAL}
            vf = fm.get(m.group(2))
            radix = m.group(2)
            digits = m.group(3).replace('_', '')
            if radix == 'b': vl = int(digits, 2)
            elif radix == 'h': vl = int(digits, 16)
            elif radix == 'o': vl = int(digits, 8)
            else: vl = int(digits)
        else:
            try: vl = int(v, 0)
            except: vl = 1 if v in ('1', 'true') else 0
    sf = SetFasmFeature(feature=ft, start=s, end=e, value=vl, value_format=vf)
    return FasmLine(set_feature=sf, annotations=ann, comment=c)

parse_fasm_filename = lambda p: list(filter(None, (_pl(l) for l in open(p))))
parse_fasm_string = lambda t: list(filter(None, (_pl(l) for l in t.split(chr(10)))))
fasm_line_to_string = lambda l, _c=False: iter([l.set_feature.feature])
canonical_features = lambda sf: iter([sf])
__version__ = 'stub'
"""

with open(init_py, 'w') as f:
    f.write(src)
