import os
import re

def replace_do_while(content):
    # This matches 'do { ... } while ( ... )'
    # We use a simple stack-based approach to find balanced braces
    new_content = ""
    i = 0
    while i < len(content):
        if content[i:i+3] == "do " or content[i:i+3] == "do{":
            # Potential do-while
            start_do = i
            brace_start = content.find("{", i)
            if brace_start != -1 and (brace_start - i < 10): # sanity check
                # Find balanced closing brace
                count = 1
                curr = brace_start + 1
                while curr < len(content) and count > 0:
                    if content[curr] == "{": count += 1
                    elif content[curr] == "}": count -= 1
                    curr += 1

                if count == 0:
                    brace_end = curr - 1
                    # Look for 'while'
                    while_match = re.match(r'\s*while\s*\(', content[brace_end+1:])
                    if while_match:
                        while_start = brace_end + 1 + while_match.start()
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
                            body = content[brace_start+1:brace_end]
                            cond = content[paren_start+1:paren_end]

                            # Replace with while ({ body; cond }) ()
                            replacement = f"while ({{ {body}; {cond} }}) ()"
                            new_content += replacement
                            i = paren_end + 1
                            continue

        new_content += content[i]
        i += 1
    return new_content

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
