import os
import re

def fix_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    orig = content

    # 1. Non-ASCII
    content = content.replace('—', '-')
    content = content.replace('–', '-')
    content = content.replace('→', '->')
    content = content.replace('§', 'section')
    content = content.replace('’', "'")
    content = content.replace('“', '"')
    content = content.replace('”', '"')

    # 2. String concat deprecation
    content = re.sub(r'throw new GZIPException\(ret \+ ": " \+ msg\)', r'throw new GZIPException(s"$ret: $msg")', content)

    # 3. GZIPHeader numeric widening
    content = content.replace('0x8b1f.toShort', '(0x8b1f & 0xffff).toShort')

    # 4. do-while replacement (simple cases)
    # Match: do { <body> } while ( <cond> )
    # Replace with: while ({ <body>; <cond> }) ()

    def repl(match):
        body = match.group(1).strip()
        cond = match.group(2).strip()
        # Ensure we don't end up with empty block issues or semicolon mess
        if not body:
            return f"while ({{ {cond} }}) ()"
        return f"while ({{ {body}; {cond} }}) ()"

    content = re.sub(r'do\s*\{([\s\S]*?)\}\s*while\s*\((.*?)\)', repl, content)

    if content != orig:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

for root, dirs, files in os.walk('core/src'):
    for file in files:
        if file.endswith('.scala'):
            if fix_file(os.path.join(root, file)):
                print(f"Cleaned {file}")
