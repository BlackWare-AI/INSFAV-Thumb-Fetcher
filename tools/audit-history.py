#!/usr/bin/env python3
"""Scan reachable history; never print matching secret values."""
import json
import re
import subprocess
import sys
def git(*args):
    return subprocess.check_output(["git", *args])
patterns = {
    "PRIVATE_KEY": rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
    "GITHUB_TOKEN": rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,})",
    "GOOGLE_API_KEY": rb"AIza[0-9A-Za-z_-]{30,}",
    "INSTAGRAM_SESSION": rb"\b[0-9]{6,}(?:%3[Aa]|:)[A-Za-z0-9_%:-]{25,}",
    "LONG_LITERAL_CREDENTIAL": rb"(?i)\b(?:sessionid|csrftoken|access_token|client_secret)\s*[:=]\s*[\"']?([A-Za-z0-9%:_-]{24,})",
}
forbidden = re.compile(r"(?i)(?:^|/)(?:local\.properties|cookies[^/]*|credentials[^/]*|.*\.(?:jks|keystore|apk|aab|enc|webp|png|jpe?g|gif|db|sqlite3?))$")
findings = []
seen = set()
for line in git("rev-list", "--objects", "--all").decode().splitlines():
    sha, _, path = line.partition(" ")
    if sha in seen or git("cat-file", "-t", sha).strip() != b"blob":
        continue
    seen.add(sha)
    if forbidden.search(path) or path.lower().endswith(".json") and path != "samples/anonymous.json":
        findings.append({"blob": sha, "path": path, "code": "FORBIDDEN_FILE"})
    data = git("cat-file", "blob", sha)
    for code, pattern in patterns.items():
        if re.search(pattern, data):
            findings.append({"blob": sha, "path": path, "code": code})
print(json.dumps({"commits": len(git("rev-list", "--all").splitlines()), "blobs": len(seen), "findings": findings}, indent=2))
sys.exit(1 if findings else 0)
