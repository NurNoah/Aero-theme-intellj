#!/usr/bin/env python3
"""Small, dependency-free validator for the static AeroVista theme plugin."""

from __future__ import annotations

import json
import re
import struct
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
THEME_PATH = RESOURCES / "themes/aerovista.theme.json"
PLUGIN_PATH = RESOURCES / "META-INF/plugin.xml"
HEX = re.compile(r"^#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?$")


def fail(message: str) -> None:
    raise AssertionError(message)


def resolve_color(value: str, colors: dict[str, str], stack: tuple[str, ...] = ()) -> str:
    if HEX.fullmatch(value):
        return value[:7]
    if value in stack:
        fail(f"Circular named color reference: {' -> '.join((*stack, value))}")
    if value not in colors:
        fail(f"Unknown named color: {value}")
    return resolve_color(colors[value], colors, (*stack, value))


def rgb(value: str) -> tuple[int, int, int]:
    return tuple(int(value[i : i + 2], 16) for i in (1, 3, 5))  # type: ignore[return-value]


def luminance(value: str) -> float:
    channels = []
    for channel in rgb(value):
        component = channel / 255
        channels.append(component / 12.92 if component <= 0.04045 else ((component + 0.055) / 1.055) ** 2.4)
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]


def contrast(a: str, b: str) -> float:
    high, low = sorted((luminance(a), luminance(b)), reverse=True)
    return (high + 0.05) / (low + 0.05)


def validate_png(path: Path) -> None:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        fail(f"Not a PNG file: {path}")
    width, height = struct.unpack(">II", data[16:24])
    if width < 1280 or height < 720:
        fail(f"Background is too small: {width}x{height}")


def main() -> int:
    theme = json.loads(THEME_PATH.read_text(encoding="utf-8"))
    plugin = ET.parse(PLUGIN_PATH).getroot()
    ET.parse(RESOURCES / "themes/AeroVista.xml")
    ET.parse(RESOURCES / "META-INF/pluginIcon.svg")
    ET.parse(RESOURCES / "META-INF/pluginIcon_dark.svg")

    if theme.get("parentTheme") != "Islands Light":
        fail("Theme must inherit from Islands Light")
    if theme.get("dark") is not False:
        fail("AeroVista is a light theme")

    provider = plugin.find("./extensions/themeProvider")
    if provider is None:
        fail("plugin.xml does not register a themeProvider")
    provider_path = provider.attrib["path"].lstrip("/")
    if not (RESOURCES / provider_path).is_file():
        fail(f"Missing theme provider resource: {provider_path}")

    for section in ("background", "emptyFrameBackground"):
        image = theme[section]["image"].lstrip("/")
        image_path = RESOURCES / image
        if not image_path.is_file():
            fail(f"Missing {section} image: {image}")
        validate_png(image_path)

    colors = theme["colors"]
    for name, value in colors.items():
        resolve_color(value, colors, (name,))

    ui = theme["ui"]
    for key, value in ui.items():
        if isinstance(value, str) and (value.startswith("#") or value in colors):
            resolve_color(value, colors)

    checks = (
        ("normal UI text", "Label.foreground", "Panel.background", 4.5),
        ("active selection", "Tree.selectionForeground", "Tree.selectionBackground", 4.5),
        ("inactive selection", "Tree.selectionInactiveForeground", "Tree.selectionInactiveBackground", 4.5),
        ("default button", "Button.default.foreground", "Button.default.endBackground", 4.5),
        ("notification text", "Notification.foreground", "Notification.background", 4.5),
    )
    results = []
    for label, foreground_key, background_key, minimum in checks:
        foreground = resolve_color(ui[foreground_key], colors)
        background = resolve_color(ui[background_key], colors)
        ratio = contrast(foreground, background)
        if ratio < minimum:
            fail(f"{label} contrast {ratio:.2f}:1 is below {minimum:.1f}:1")
        results.append(f"{label} {ratio:.2f}:1")

    print("AeroVista theme validation passed")
    print(f"  {len(ui)} UI overrides, {len(colors)} named colors")
    for result in results:
        print(f"  {result}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, KeyError, json.JSONDecodeError, ET.ParseError) as error:
        print(f"Theme validation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
