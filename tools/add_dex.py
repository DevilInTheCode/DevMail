#!/usr/bin/env python3
"""Append classes.dex to an unsigned APK produced by aapt2."""
import os, shutil, sys, zipfile

src, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]
if os.path.exists(out):
    os.remove(out)
shutil.copyfile(src, out)
with zipfile.ZipFile(out, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
print("added classes.dex (%d bytes) -> %s" % (os.path.getsize(dex), out))
