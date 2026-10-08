# SPDX-License-Identifier: GPL-3.0-only
"""Copy the selected upstream public APK's runtime assets to an external directory."""
import argparse
import hashlib
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('upstream_apk', type=Path)
parser.add_argument('external_assets_directory', type=Path)
args = parser.parse_args()
expected = '8c555ce179f30a30b659914f70355245a594f82957424bf44fc532fb1409441e'
if hashlib.sha256(args.upstream_apk.read_bytes()).hexdigest() != expected:
    raise SystemExit('Input must be the verified public DiPlay-0.2.10.apk release')
repo = Path(__file__).resolve().parents[1]
destination = args.external_assets_directory.resolve()
if destination.is_relative_to(repo):
    raise SystemExit('Select a runtime-assets directory outside this source tree')
with zipfile.ZipFile(args.upstream_apk) as archive:
    for name in ('identity.pk8', 'certificate.p7b'):
        data = archive.read('assets/offline-mfi/' + name)
        if not data:
            raise SystemExit('Upstream runtime asset is empty')
        target = destination / 'offline-mfi' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
print('Prepared the two public-release runtime assets; file contents are not logged.')
