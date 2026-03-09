import os
import re

def fix_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    orig = content
    # Simple replacement for well-structured do-while
    # We use a non-greedy match for the body.
    content = re.sub(r'do\s*\{([\s\S]*?)\}\s*while\s*\((.*?)\)', r'while ({ \1; \2 }) ()', content)

    if content != orig:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)

for p in ['core/src/com/jcraft/jzlib/Deflate.scala', 'core/src/com/jcraft/jzlib/Tree.scala', 'core/src/com/jcraft/jzlib/InflaterInputStream.scala', 'core/src/com/jcraft/jzlib/GZIPInputStream.scala', 'core/src/com/jcraft/jzlib/Adler32.scala']:
    fix_file(p)
