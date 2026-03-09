import os
import re

def fix_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    orig = content

    # Simple do-while replacement that handles multiline
    def repl(match):
        body = match.group(1).strip()
        cond = match.group(2).strip()
        # If body is empty, just while(cond)
        if not body:
            return f"while ({cond}) {{}}"
        # Standard Scala 3 rewrite for do-while
        return f"while ({{ {body}; {cond} }}) ()"

    content = re.sub(r'\bdo\s*\{([\s\S]*?)\}\s*while\s*\((.*?)\)', repl, content)

    if content != orig:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

# Manual fix for the problematic GZIPInputStream syntax error
def fix_gzip_input_stream():
    path = 'core/src/com/jcraft/jzlib/GZIPInputStream.scala'
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Revert the broken while({ ... })
    content = content.replace('while ({ if (inflater.avail_in <= 0) {', 'do { if (inflater.avail_in <= 0) {')
    content = content.replace('if (err != 0) return false; inflater.istate.inParsingHeader( }) ())', 'if (err != 0) return false } while (inflater.istate.inParsingHeader())')

    # Revert second one
    content = content.replace('throw new IOException(inflater.msg); inflater.istate.inParsingHeader( }) ())', 'throw new IOException(inflater.msg) } while (inflater.istate.inParsingHeader())')

    # Now use a safer replacement
    content = content.replace('do {', '{ var loop_flag = true; while (loop_flag) {')
    # This is getting messy. I'll just use a clean string replacement for the two loops.

    loop1_old = """    val b1 = new Array[Byte](1)
    do {
      if (inflater.avail_in <= 0) {
        val i = in.read(b1)
        if (i <= 0) return false
        inflater.setInput(b1, 0, 1, true)
      }
      val err = inflater.inflate(Z_NO_FLUSH)
      if (err != 0) return false
    } while (inflater.istate.inParsingHeader())"""

    loop1_new = """    val b1 = new Array[Byte](1)
    var continueParsing = true
    while (continueParsing) {
      if (inflater.avail_in <= 0) {
        val i = in.read(b1)
        if (i <= 0) return false
        inflater.setInput(b1, 0, 1, true)
      }
      val err = inflater.inflate(Z_NO_FLUSH)
      if (err != 0) return false
      continueParsing = inflater.istate.inParsingHeader()
    }"""

    # content = content.replace(loop1_old, loop1_new)
    # The literal replacement might fail due to whitespace. I'll just rewrite the file.
    return content

for root, dirs, files in os.walk('core/src'):
    for file in files:
        if file.endswith('.scala'):
            fix_file(os.path.join(root, file))
