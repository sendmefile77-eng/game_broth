#!/usr/bin/env python3
"""Preserve app-private models and save across a one-time Android signing-key change.

Requires Android platform-tools (adb), a USB-debuggable device, and a debuggable
installed Game Broth APK. The script NEVER uninstalls or installs an APK.

Usage:
  python scripts/migrate_debug_install.py backup game-broth-device.tar
  # After the backup is verified, uninstall old APK and install a new debug APK.
  python scripts/migrate_debug_install.py restore game-broth-device.tar
"""

import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile


PACKAGE = "com.sendmefile77.gamebroth"
APP_DIR = f"/data/user/0/{PACKAGE}"
ROOTS = ("files/", "databases/", "shared_prefs/")
CHUNK = 4 * 1024 * 1024


def adb(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    result = subprocess.run(["adb", *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if check and result.returncode:
        raise RuntimeError(result.stderr.decode(errors="replace").strip() or result.stdout.decode(errors="replace").strip())
    return result


def require_device() -> None:
    if not shutil.which("adb"):
        raise RuntimeError("adb не найден. Установите Android platform-tools на ПК.")
    devices = adb("devices").stdout.decode(errors="replace").splitlines()[1:]
    connected = [line for line in devices if line.endswith("\tdevice")]
    if len(connected) != 1:
        raise RuntimeError("Подключите ровно один телефон и разрешите отладку USB: adb devices")
    result = adb("shell", "run-as", PACKAGE, "id", check=False)
    if result.returncode or b"uid=" not in result.stdout:
        raise RuntimeError("run-as недоступен. Установленная игра должна быть отладочным APK. Ничего не удаляйте.")


def validate_archive(path: Path) -> tuple[int, int]:
    count = total = 0
    model_found = False
    database_found = False
    with tarfile.open(path, mode="r|") as archive:
        for member in archive:
            name = member.name.removeprefix("./").rstrip("/")
            if (member.name.startswith("/") or
                    any(part in ("", ".", "..") for part in name.split("/")) or
                    not any(name == root[:-1] or name.startswith(root) for root in ROOTS)):
                raise RuntimeError(f"Неожиданный путь в архиве: {name}")
            if not (member.isfile() or member.isdir()):
                raise RuntimeError(f"Ссылки и специальные файлы запрещены: {name}")
            count += 1
            if member.isfile():
                stream = archive.extractfile(member)
                if stream is None:
                    raise RuntimeError(f"Не удалось прочитать {name}")
                size = 0
                while block := stream.read(CHUNK):
                    size += len(block)
                if size != member.size:
                    raise RuntimeError(f"Файл {name} обрезан: {size} вместо {member.size}")
                total += size
                if name.startswith("files/models/"):
                    model_found = True
                if name == "databases/game_broth.db":
                    database_found = True
    if not count or not database_found:
        raise RuntimeError("Архив не содержит сохранение игры. Не удаляйте установленное приложение.")
    if not model_found:
        raise RuntimeError("Архив не содержит моделей. Не удаляйте установленное приложение.")
    return count, total


def backup(path: Path) -> None:
    require_device()
    if path.exists():
        raise RuntimeError(f"Файл уже существует: {path}. Выберите новое имя.")
    path.parent.mkdir(parents=True, exist_ok=True)
    adb("shell", "am", "force-stop", PACKAGE)
    # exec-out does not allocate a terminal, so the TAR bytes remain intact.
    cmd = ["adb", "exec-out", "run-as", PACKAGE, "tar", "-cf", "-", "-C", APP_DIR,
           "files", "databases", "shared_prefs"]
    digest = hashlib.sha256()
    try:
        with subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE) as process:
            assert process.stdout is not None
            with path.open("xb") as target:
                while block := process.stdout.read(CHUNK):
                    target.write(block)
                    digest.update(block)
            stderr = process.stderr.read().decode(errors="replace") if process.stderr else ""
            if process.wait() != 0:
                raise RuntimeError(f"Копирование с телефона не удалось: {stderr}")
        count, total = validate_archive(path)
    except BaseException:
        path.unlink(missing_ok=True)
        raise
    print(f"Резервная копия проверена: {path} · {count} записей · {total:,} байт данных")
    print(f"SHA-256: {digest.hexdigest()}")
    print("Теперь можно отдельно удалить старое приложение и установить новый debug APK.")
    print(f"После установки выполните: python {Path(__file__).name} restore {path}")


def restore(path: Path) -> None:
    if not path.is_file():
        raise RuntimeError(f"Архив не найден: {path}")
    count, total = validate_archive(path)
    require_device()
    if input("Новый APK уже установлен, а старый удалён? Напишите ВОССТАНОВИТЬ: ").strip() != "ВОССТАНОВИТЬ":
        print("Отмена. Архив не изменён.")
        return
    adb("shell", "am", "force-stop", PACKAGE)
    cmd = ["adb", "shell", "-T", "run-as", PACKAGE, "tar", "-xf", "-", "-C", APP_DIR]
    with path.open("rb") as source:
        with subprocess.Popen(cmd, stdin=source, stdout=subprocess.PIPE, stderr=subprocess.PIPE) as process:
            out, err = process.communicate()
            if process.returncode:
                raise RuntimeError(f"Восстановление прервано: {(err or out).decode(errors='replace')}")
    # Runtime libraries come from the new APK, not from the old installation.
    adb("shell", "run-as", PACKAGE, "rm", "-rf", f"{APP_DIR}/files/local_dream_runtime")
    print(f"Данные восстановлены: {count} записей, {total:,} байт. Запустите игру.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("backup", "restore"))
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    try:
        (backup if args.action == "backup" else restore)(args.archive)
    except (RuntimeError, OSError, tarfile.TarError) as error:
        print(f"ОШИБКА: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
