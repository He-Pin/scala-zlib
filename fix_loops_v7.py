import os
import re

def find_balanced(s, start, open_char='{', close_char='}'):
    count = 0
    for i in range(start, len(s)):
        if s[i] == open_char:
            count += 1
        elif s[i] == close_char:
            count -= 1
            if count == 0:
                return i
    return -1

def replace_do_while(content):
    pos = 0
    while True:
        match = re.search(r'\bdo\s*\{', content[pos:])
        if not match:
            break

        start_do = pos + match.start()
        brace_start = pos + match.end() - 1
        brace_end = find_balanced(content, brace_start)

        if brace_end == -1:
            pos = brace_start + 1
            continue

        remaining = content[brace_end+1:]
        match_while = re.match(r'\s*while\s*\(', remaining)
        if not match_while:
            pos = brace_end + 1
            continue

        paren_start = brace_end + 1 + match_while.end() - 1
        paren_end = find_balanced(content, paren_start, '(', ')')

        if paren_end == -1:
            pos = brace_end + 1
            continue

        body = content[brace_start+1:brace_end].strip()
        cond = content[paren_start+1:paren_end].strip()

        # New loop using a flag
        var_name = f"continueLoop_{start_do}"
        new_loop = f"{{ var {var_name} = true; while ({var_name}) {{ {body}\n {var_name} = ({cond}) }} }}"

        # Special case for empty body
        if not body:
            new_loop = f"while ({cond}) {{}}"

        content = content[:start_do] + new_loop + content[paren_end+1:]
        pos = start_do + len(new_loop)

    return content

for root, dirs, files in os.walk('core/src'):
    for file in files:
        if file.endswith('.scala'):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()
            new_content = replace_do_while(content)
            if new_content != content:
                with open(path, 'w', encoding='utf-8') as f:
                    f.write(new_content)
