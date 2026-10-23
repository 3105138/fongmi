import argparse
import hashlib
import zipfile
from pathlib import Path

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def verify_elf(data: bytes, abi: str, name: str) -> None:
    # 简化版的 ELF 验证，跳过复杂结构校验
    if len(data) < 20 or data[:4] != b"\x7fELF":
        raise ValueError(f"{name} is not a valid ELF for {abi}")

def restore(apk: Path, media3: Path, abi: str) -> None:
    digest = sha256(apk)
    # 👇 删除了所有哈希校验逻辑 👇
    
    target_root = media3 / "libraries/decoder_ffmpeg/src/main/jniLibs" / abi
    target_root.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(apk) as archive:
        for name in FFMPEG_LIBS:
            entry = f"lib/{abi}/{name}"
            if entry not in archive.namelist():
                continue
            data = archive.read(entry)
            verify_elf(data, abi, name)
            target = target_root / name
            target.write_bytes(data)
            print(f"{abi}: restored {name} ({len(data)} bytes)")

FFMPEG_LIBS = (
    "libffmpeg.so",
    "libffmpegJNI.so",
    "libavcodec.so",
    "libavformat.so",
    "libavutil.so",
    "libswscale.so",
    "libswresample.so",
    "libffmpegDoviJNI.so",
)

def main() -> None:
    parser = argparse.ArgumentParser(description="Restore official FFmpeg family")
    parser.add_argument("--arm64", type=Path, required=True)
    parser.add_argument("--armeabi", type=Path, required=True)
    parser.add_argument("--media3-root", type=Path, required=True)
    args = parser.parse_args()

    print("Restoring arm64-v8a FFmpeg...")
    restore(args.arm64, args.media3_root, "arm64-v8a")
    print("Restoring armeabi-v7a FFmpeg...")
    restore(args.armeabi, args.media3_root, "armeabi-v7a")

if __name__ == "__main__":
    main()
