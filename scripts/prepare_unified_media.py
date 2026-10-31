"""Build and package Media3 against the selected unified FFmpeg artifact."""

import argparse
from pathlib import Path
import shutil
from zipfile import ZIP_DEFLATED, ZipFile

ABIS = ("arm64-v8a", "armeabi-v7a")


def prepare(media, native):
    main = media / "libraries/decoder_ffmpeg/src/main"
    cmake = main / "jni/CMakeLists.txt"
    content = cmake.read_text()
    start = content.index("foreach(ffmpeg_lib avutil swresample avcodec avformat)")
    end = content.index("endforeach()", start) + len("endforeach()")
    content = content[:start] + '''add_library(unified_ffmpeg SHARED IMPORTED)
set_target_properties(unified_ffmpeg PROPERTIES
    IMPORTED_LOCATION "${ffmpeg_binaries}/libffmpeg.so")''' + content[end:]
    for library in ("swresample", "avcodec", "avformat", "avutil"):
        content = content.replace("PRIVATE " + library, "PRIVATE unified_ffmpeg")
    cmake.write_text(content)
    for abi in ABIS:
        target = main / "jniLibs" / abi
        target.mkdir(parents=True, exist_ok=True)
        shutil.copy2(native / abi / "libffmpeg.so", target / "libffmpeg.so")


def rewrite(archive, libraries):
    # Validate every input before replacing the archive.
    for data in libraries.values():
        if not data:
            raise ValueError(f"Empty native library for {archive}")
    temporary = archive.with_suffix(".unified.tmp")
    with ZipFile(archive) as src, ZipFile(temporary, "w", ZIP_DEFLATED) as dst:
        for item in src.infolist():
            if not (item.filename.startswith("jni/") and item.filename.endswith(".so")):
                dst.writestr(item, src.read(item.filename))
        for name, data in libraries.items():
            dst.writestr(name, data)
    temporary.replace(archive)


def package(native, output):
    mpv = {}
    ffmpeg = {}
    decoder = output / "lib-decoder-ffmpeg-release.aar"
    with ZipFile(decoder) as archive:
        for abi in ABIS:
            for library in ("libmpv.so", "libplayer.so", "libc++_shared.so"):
                mpv[f"jni/{abi}/{library}"] = (native / abi / library).read_bytes()
            ffmpeg[f"jni/{abi}/libffmpeg.so"] = (native / abi / "libffmpeg.so").read_bytes()
            # The JNI must have been rebuilt by prepare + Media3 assembleRelease.
            name = f"jni/{abi}/libffmpegJNI.so"
            ffmpeg[name] = archive.read(name)
            optional = f"jni/{abi}/libffmpegDoviJNI.so"
            if optional in archive.namelist():
                ffmpeg[optional] = archive.read(optional)
    rewrite(output / "lib-mpvplayer-release.aar", mpv)
    rewrite(decoder, ffmpeg)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("prepare", "package"))
    parser.add_argument("--media-root", type=Path, default=Path("official-media3"))
    parser.add_argument("--native-root", type=Path, default=Path("native-input/mpv"))
    parser.add_argument("--output", type=Path, default=Path("app/libs"))
    args = parser.parse_args()
    if args.mode == "prepare":
        prepare(args.media_root, args.native_root)
    else:
        package(args.native_root, args.output)
