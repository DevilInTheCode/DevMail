#!/usr/bin/env python3
"""zip-alignment: выравнивает данные записей APK по 4 байта (нужно для resources.arsc, targetSdk 30+)."""
import struct
import sys
import zipfile


def align(src, dst):
    zin = zipfile.ZipFile(src, "r")
    zout = zipfile.ZipFile(dst, "w")
    try:
        for info in zin.infolist():
            data = zin.read(info.filename)
            zi = zipfile.ZipInfo(info.filename, info.date_time)
            zi.compress_type = info.compress_type
            zi.external_attr = info.external_attr
            zi.internal_attr = info.internal_attr
            zi.create_system = info.create_system
            name_len = len(info.filename.encode("utf-8"))
            base = zout.fp.tell() + 30 + name_len
            pad = (4 - (base % 4)) % 4
            if pad:
                total = pad if pad >= 4 else pad + 4
                zi.extra = struct.pack("<HH", 0x1234, total - 4) + b"\x00" * (total - 4)
            else:
                zi.extra = b""
            zout.writestr(zi, data)
    finally:
        zout.close()
        zin.close()


if __name__ == "__main__":
    align(sys.argv[1], sys.argv[2])
    print("aligned ->", sys.argv[2])
