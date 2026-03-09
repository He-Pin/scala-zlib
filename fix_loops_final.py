import os
import re

def fix_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    orig = content

    # Replacement for do { body } while (cond)
    # We use a stack-based balancer to handle nested braces correctly.
    new_content = ""
    i = 0
    while i < len(content):
        if content[i:i+2] == "do":
            # Check if it's "do {" or "do {"
            remaining = content[i+2:]
            m = re.match(r'\s*\{', remaining)
            if m:
                brace_start = i + 2 + m.end() - 1
                # Find balanced closing brace
                count = 1
                curr = brace_start + 1
                while curr < len(content) and count > 0:
                    if content[curr] == "{": count += 1
                    elif content[curr] == "}": count -= 1
                    curr += 1

                if count == 0:
                    brace_end = curr - 1
                    # Look for 'while ('
                    while_match = re.match(r'\s*while\s*\(', content[brace_end+1:])
                    if while_match:
                        paren_start = brace_end + 1 + while_match.end() - 1
                        # Find balanced closing paren
                        pcount = 1
                        pcurr = paren_start + 1
                        while pcurr < len(content) and pcount > 0:
                            if content[pcurr] == "(": pcount += 1
                            elif content[pcurr] == ")": pcount -= 1
                            pcurr += 1

                        if pcount == 0:
                            paren_end = pcurr - 1
                            body = content[brace_start+1:brace_end].strip()
                            cond = content[paren_start+1:paren_end].strip()

                            # Standard Scala 3 rewrite
                            replacement = f"while ({{ {body}; {cond} }}) ()"
                            new_content += replacement
                            i = paren_end + 1
                            continue

        # Handle do {} while (cond) -> while (cond) {}
        if content[i:i+5] == "do {}":
            remaining = content[i+5:]
            while_match = re.match(r'\s*while\s*\(', remaining)
            if while_match:
                 paren_start = i + 5 + while_match.end() - 1
                 pcount = 1
                 pcurr = paren_start + 1
                 while pcurr < len(content) and pcount > 0:
                     if content[pcurr] == "(": pcount += 1
                     elif content[pcurr] == ")": pcount -= 1
                     pcurr += 1
                 if pcount == 0:
                     paren_end = pcurr - 1
                     cond = content[paren_start+1:paren_end].strip()
                     replacement = f"while ({cond}) {{}}"
                     new_content += replacement
                     i = paren_end + 1
                     continue

        new_content += content[i]
        i += 1

    if new_content != orig:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    return False

for root, dirs, files in os.walk('core/src'):
    for file in files:
        if file.endswith('.scala'):
            fix_file(os.path.join(root, file))
