#!/usr/bin/env python3
"""工程静态自检：在没有 Android SDK 的环境里尽可能提前发现编译失败问题。

检查项
1. 所有 XML 可解析
2. Kotlin 括号配对（用真正的词法扫描，跳过字符串/字符字面量/注释）
3. stringResource 等 R.string 引用是否已定义
4. XML 资源交叉引用（@string/@mipmap/@style/@xml...），跳过库自带资源
5. 布局里引用的自定义 View 类是否存在
6. Manifest 里声明的 Activity / Service / Provider 是否有对应 Kotlin 类

用法: python3 tools/check_project.py
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main")
RES = os.path.join(SRC, "res")
JAVA = os.path.join(SRC, "java")

# 由依赖库提供的 style / attr，不属于本工程定义，跳过校验
LIB_STYLE_PREFIXES = (
    "Widget.", "TextAppearance.", "Theme.", "Base.",
    "Widget.Material3", "Widget.MaterialComponents",
)
# 系统自带的 drawable 等，@android:xxx 一律跳过
SYSTEM_REF_RE = re.compile(r"@(android|androidx):")

problems: list[str] = []
notes: list[str] = []


def add_problem(m: str) -> None:
    problems.append(m)


def add_note(m: str) -> None:
    notes.append(m)


# =====================================================================
# Kotlin 词法扫描：剔除注释与字面量后再统计括号
# =====================================================================
def strip_code(code: str) -> str:
    """移除注释和字面量，保留括号结构。"""
    out = []
    i, n = 0, len(code)
    while i < n:
        c = code[i]
        nxt = code[i + 1] if i + 1 < n else ""

        if c == "/" and nxt == "/":                     # 行注释
            j = code.find("\n", i)
            i = n if j < 0 else j
            continue
        if c == "/" and nxt == "*":                     # 块注释（支持嵌套标记 */
            j = code.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        if c == '"':                                    # 字符串，可能是 """ 三引号
            if code.startswith('"""', i):
                j = code.find('"""', i + 3)
                i = n if j < 0 else j + 3
            else:
                j = i + 1
                while j < n:
                    if code[j] == "\\":
                        j += 2
                        continue
                    if code[j] == '"':
                        break
                    j += 1
                i = min(j + 1, n)
            out.append('""')
            continue
        if c == "'":                                    # 字符字面量
            j = i + 1
            while j < n:
                if code[j] == "\\":
                    j += 2
                    continue
                if code[j] == "'":
                    break
                j += 1
            i = min(j + 1, n)
            out.append("''")
            continue

        out.append(c)
        i += 1
    return "".join(out)


def check_brackets(path: str, code: str) -> None:
    pairs = {"(": ")", "[": "]", "{": "}"}
    closers = {v: k for k, v in pairs.items()}
    stack: list[tuple[str, int]] = []
    line = 1
    for ch in code:
        if ch == "\n":
            line += 1
        elif ch in pairs:
            stack.append((ch, line))
        elif ch in closers:
            if not stack:
                add_problem(f"[括号多余] {rel(path)}: 第 {line} 行多出一个 '{ch}'")
                return
            top, ln = stack.pop()
            if pairs[top] != ch:
                add_problem(
                    f"[括号不匹配] {rel(path)}: 第 {ln} 行的 '{top}' "
                    f"被第 {line} 行的 '{ch}' 关闭"
                )
                return
    for ch, ln in stack:
        add_problem(f"[括号未闭合] {rel(path)}: 第 {ln} 行的 '{ch}' 没有对应的 '{pairs[ch]}'")


def rel(p: str) -> str:
    return os.path.relpath(p, ROOT)


def read(p: str) -> str:
    with open(p, encoding="utf-8") as f:
        return f.read()


# =====================================================================
# 资源收集
# =====================================================================
RES_KINDS = ("string", "mipmap", "style", "xml", "color", "drawable", "layout", "array")


def collect_res() -> dict[str, set[str]]:
    res = {k: set() for k in RES_KINDS}
    for dirpath, _, filenames in os.walk(RES):
        folder = os.path.basename(dirpath)
        kind = folder.split("-")[0]
        for name in filenames:
            if not name.endswith(".xml"):
                if kind in res:
                    res[kind].add(os.path.splitext(name)[0])
                continue
            path = os.path.join(dirpath, name)
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError:
                continue
            if folder.startswith("values"):
                for child in root:
                    tag = child.tag
                    # <string-array>/<integer-array> 都对应 R.array.*
                    if tag in ("string-array", "integer-array", "array"):
                        tag = "array"
                    if tag in res:
                        res[tag].add(child.attrib.get("name", ""))
            else:
                if kind in res:
                    res[kind].add(os.path.splitext(name)[0])
    return res


def is_lib_ref(kind: str, key: str) -> bool:
    if kind == "style" and key.startswith(LIB_STYLE_PREFIXES):
        return True
    return False


# =====================================================================
# 各项检查
# =====================================================================
def check_xml_wellformed() -> None:
    targets = [os.path.join(SRC, "AndroidManifest.xml")]
    for dirpath, _, filenames in os.walk(RES):
        for name in filenames:
            if name.endswith(".xml"):
                targets.append(os.path.join(dirpath, name))
    for p in targets:
        if not os.path.exists(p):
            continue
        try:
            ET.parse(p)
        except ET.ParseError as e:
            add_problem(f"[XML 语法错误] {rel(p)}: {e}")


def check_res_refs() -> None:
    res = collect_res()
    files = [os.path.join(SRC, "AndroidManifest.xml")]
    for dirpath, _, filenames in os.walk(RES):
        for name in filenames:
            if name.endswith(".xml"):
                files.append(os.path.join(dirpath, name))

    for p in files:
        if not os.path.exists(p):
            continue
        content = read(p)
        content = SYSTEM_REF_RE.sub("@skip:", content)      # @android:xxx 跳过
        for m in re.finditer(r"@(string|mipmap|style|xml|color|drawable|layout|array)/([\w.]+)", content):
            kind, key = m.group(1), m.group(2)
            if is_lib_ref(kind, key):
                continue
            # array 引用可能写成 @array/xposed_scope
            if kind in res and key not in res[kind]:
                add_problem(
                    f"[资源引用缺失] {rel(p)}: @{kind}/{key} 未定义"
                    f"（已定义: {sorted(res[kind])[:8]}）"
                )


def check_code_refs() -> None:
    res = collect_res()
    kt_files = []
    for dirpath, _, filenames in os.walk(JAVA):
        for name in filenames:
            if name.endswith(".kt"):
                kt_files.append(os.path.join(dirpath, name))

    defined_strings = res.get("string", set())

    for path in kt_files:
        code = read(path)
        check_brackets(path, strip_code(code))
        body = strip_code(code)

        # R.string.xxx / getString(R.string.xxx) 引用校验
        for m in re.finditer(r"R\.string\.(\w+)", body):
            if m.group(1) not in defined_strings:
                add_problem(f"[字符串缺失] {rel(path)}: R.string.{m.group(1)} 未定义")
        # 布局/资源引用
        for m in re.finditer(r"R\.(layout|drawable|mipmap|xml|array|style|color)\.(\w+)", body):
            kind, key = m.group(1), m.group(2)
            if kind in res and key not in res[kind]:
                add_problem(f"[资源引用缺失] {rel(path)}: R.{kind}.{key} 未定义")

    return kt_files


def check_custom_views(kt_files: list[str]) -> None:
    """布局里引用的自定义 View 必须有对应类"""
    qualnames = set()
    for p in kt_files:
        code = read(p)
        m = re.search(r"^package\s+([\w.]+)", code, flags=re.M)
        pkg = m.group(1) if m else ""
        for cm in re.finditer(r"^\s*(?:class|object)\s+([A-Z]\w*)", code, flags=re.M):
            qualnames.add(f"{pkg}.{cm.group(1)}" if pkg else cm.group(1))

    for dirpath, _, filenames in os.walk(os.path.join(RES, "layout")):
        for name in filenames:
            if not name.endswith(".xml"):
                continue
            path = os.path.join(dirpath, name)
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError:
                continue
            for el in root.iter():
                tag = el.tag
                if "." in tag and not tag.startswith(("android.", "androidx.", "com.google.")):
                    if tag not in qualnames:
                        add_problem(f"[自定义 View 缺失] {rel(path)}: {tag} 未找到对应类")


def check_manifest_components(kt_files: list[str]) -> None:
    manifest = os.path.join(SRC, "AndroidManifest.xml")
    if not os.path.exists(manifest):
        return
    content = read(manifest)
    simple_names = set()
    for p in kt_files:
        simple_names.add(os.path.splitext(os.path.basename(p))[0])
    for m in re.finditer(r'android:name="\.([\w.$]+)"', content):
        name = m.group(1).replace("$", ".")
        short = name.split(".")[-1]
        if short not in simple_names:
            add_problem(f"[组件缺失] AndroidManifest.xml: .{name} 对应的 {short}.kt 不存在")


def main() -> int:
    check_xml_wellformed()
    kt_files = check_code_refs() or []
    check_custom_views(kt_files)
    check_manifest_components(kt_files)
    check_res_refs()

    print("=" * 64)
    if problems:
        print(f"发现 {len(problems)} 个必须修复的问题：\n")
        for p in problems:
            print("  ✗", p)
    else:
        print("✓ 未发现会导致编译失败的问题")
    print("=" * 64)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
