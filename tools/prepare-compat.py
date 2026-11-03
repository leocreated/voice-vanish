"""Unpack declared nested jars for Loom development runs only, not distribution."""
import io
import json
import sys
from pathlib import Path
from zipfile import ZipFile

root = Path(sys.argv[1]).resolve()
visited = set()

def extract(path):
    if path.name in visited:
        return
    visited.add(path.name)
    with ZipFile(path) as jar:
        if 'fabric.mod.json' not in jar.namelist():
            return
        metadata = json.loads(jar.read('fabric.mod.json'))
        for nested in metadata.get('jars', []):
            name = nested['file']
            target = root / Path(name).name
            target.write_bytes(jar.read(name))
            print('Development dependency:', target.name)
            extract(target)

for jar in list(root.glob('*.jar')):
    extract(jar)
