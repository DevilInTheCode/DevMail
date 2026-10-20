#!/usr/bin/env python3
"""Добавляет META-INF-ресурсы JavaMail/Activation в APK (провайдеры, mailcap, charset map)."""
import sys, zipfile

apk = sys.argv[1]
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    have = set(z.namelist())
    for jar in sys.argv[2:]:
        with zipfile.ZipFile(jar) as zj:
            for name in zj.namelist():
                if not name.startswith("META-INF/") or name.endswith("/"):
                    continue
                if name.endswith(".class") or name == "META-INF/MANIFEST.MF":
                    continue
                if name in have:
                    continue
                z.writestr(name, zj.read(name))
                have.add(name)
                print("+", name)
