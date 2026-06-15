"""
APP 图标生成脚本 —— 基于用户提供的原图直接缩放，不再自己绘制
用法：
  1. 将你提供的图标原图放到同目录，命名为 icon_source.png（或 512x512 以上的任意 PNG）
  2. 运行：python gen_icon_from_source.py
"""
from PIL import Image
import os

SOURCE_PATH = r"c:\Users\Squema-Mini\Documents\LRS\wolfcha-android\icon_source.png"
RES_BASE = r"c:\Users\Squema-Mini\Documents\LRS\wolfcha-android\app\src\main\res"

# ============== mipmap 完整图标尺寸（带背景）==============
MIPMAP_SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

# ============== drawable 前景图标尺寸（透明背景，供 adaptive-icon 使用）==============
# adaptive-icon 的 foreground 需要比完整图标大（inner 72dp + outer 108dp）
FG_SIZES = {
    'drawable-mdpi': 108,
    'drawable-hdpi': 162,
    'drawable-xhdpi': 216,
    'drawable-xxhdpi': 324,
    'drawable-xxxhdpi': 432,
}

def load_source():
    """加载用户提供的原图。如果不存在，报错并提示。"""
    if not os.path.exists(SOURCE_PATH):
        alt = SOURCE_PATH.replace('.png', '.jpg')
        if os.path.exists(alt):
            return Image.open(alt).convert('RGBA')
        print(f"ERROR: 找不到原图 {SOURCE_PATH}")
        print(f"请把你给的那张狼人图标保存为：{SOURCE_PATH}")
        print(f"（也支持 .jpg 格式，保存为 icon_source.jpg）")
        raise FileNotFoundError(SOURCE_PATH)
    return Image.open(SOURCE_PATH).convert('RGBA')

def make_transparent_foreground(src_img, size_px):
    """从原图生成透明背景的前景图：把黄色圆形背景抠掉，只保留黑色狼人剪影。
    方法：把接近黄色的像素设为透明，其余保留。
    """
    # 先缩放到目标尺寸
    img = src_img.resize((size_px, size_px), Image.LANCZOS)
    pixels = img.load()
    for y in range(size_px):
        for x in range(size_px):
            r, g, b, a = pixels[x, y]
            # 判定是否是背景黄色（R≈255, G≈200-230, B≈10-50）或白色边角
            is_yellow_bg = (r > 200 and g > 150 and b < 120)
            is_white_corner = (r > 240 and g > 240 and b > 240)
            if is_yellow_bg or is_white_corner:
                pixels[x, y] = (0, 0, 0, 0)  # 完全透明
    return img

def main():
    os.makedirs(RES_BASE, exist_ok=True)
    src = load_source()
    src_w, src_h = src.size
    print(f"加载原图: {src_w}x{src_h}")

    # ========== 1. 生成 mipmap 完整图标（直接缩放原图）==========
    for folder, px in MIPMAP_SIZES.items():
        folder_path = os.path.join(RES_BASE, folder)
        os.makedirs(folder_path, exist_ok=True)
        resized = src.resize((px, px), Image.LANCZOS)
        resized.save(os.path.join(folder_path, 'ic_launcher.png'), 'PNG')
        resized.save(os.path.join(folder_path, 'ic_launcher_round.png'), 'PNG')
        print(f"[mipmap] {folder}/ic_launcher.png ({px}x{px})")

    # ========== 2. 生成 drawable 前景图（透明背景，仅狼人剪影）==========
    for folder, px in FG_SIZES.items():
        folder_path = os.path.join(RES_BASE, folder)
        os.makedirs(folder_path, exist_ok=True)
        fg = make_transparent_foreground(src, px)
        fg.save(os.path.join(folder_path, 'ic_launcher_foreground.png'), 'PNG')
        print(f"[fg]     {folder}/ic_launcher_foreground.png ({px}x{px})")

    # ========== 3. 预览图 ==========
    preview_dir = os.path.join(RES_BASE, 'drawable')
    os.makedirs(preview_dir, exist_ok=True)
    preview = src.resize((512, 512), Image.LANCZOS)
    preview.save(os.path.join(preview_dir, 'ic_launcher_preview.png'), 'PNG')
    print(f"[preview] drawable/ic_launcher_preview.png (512x512)")

    print("\n=== 全部生成完成 ===")

if __name__ == '__main__':
    main()
