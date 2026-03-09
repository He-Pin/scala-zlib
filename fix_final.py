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

    # 2. Warnings
    content = content.replace('throw new GZIPException(ret + ": " + msg)', 'throw new GZIPException(s": ")')
    content = content.replace('0x8b1f.toShort', '(0x8b1f & 0xffff).toShort')

    # 3. do-while replacement for Scala 3
    # Use a simpler while loop with a boolean flag
    # do { body } while (cond) -> { var loop = true; while (loop) { body; loop = cond } }

    def find_balanced(s, start):
        count = 0
        for i in range(start, len(s)):
            if s[i] == '{':
                count += 1
            elif s[i] == '}':
                count -= 1
                if count == 0:
                    return i
        return -1

    pos = 0
    while True:
        match = re.search(r'\bdo\s*\{', content[pos:])
        if not match:
            break

        start_do = pos + match.start()
        start_brace = pos + match.end() - 1
        end_brace = find_balanced(content, start_brace)

        if end_brace == -1:
            pos = start_brace + 1
            continue

        remaining = content[end_brace+1:]
        match_while = re.match(r'\s*while\s*\(', remaining)
        if not match_while:
            pos = end_brace + 1
            continue

        start_while_paren = end_brace + 1 + match_while.end() - 1
        paren_count = 0
        end_while_paren = -1
        for i in range(start_while_paren, len(content)):
            if content[i] == '(':
                paren_count += 1
            elif content[i] == ')':
                paren_count -= 1
                if paren_count == 0:
                    end_while_paren = i
                    break

        if end_while_paren == -1:
            pos = end_brace + 1
            continue

        body = content[start_brace+1:end_brace].strip()
        cond = content[start_while_paren+1:end_while_paren].strip()

        # Unique variable name
        var_name = f"continueLoop_{start_do}"
        new_loop = f"{{ var {var_name} = true; while ({var_name}) {{ {body}\n {var_name} = ({cond}) }} }}"

        content = content[:start_do] + new_loop + content[end_while_paren+1:]
        pos = start_do + len(new_loop)

    # 4. Special case: do {} while (cond)
    content = re.sub(r'do\s*\{\s*\}\s*while\s*\((.*?)\)', r'while (\1) {}', content)

    if content != orig:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

for root, dirs, files in os.walk('core/src'):
    for file in files:
        if file.endswith('.scala'):
            fix_file(os.path.join(root, file))
