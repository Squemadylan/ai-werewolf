from PIL import Image, ImageDraw

def _draw_wolf_content(draw, size, include_yellow_bg=True, include_yellow_mouth=True):
    """在 draw 上绘制狼人的所有内容元素（爪痕、云朵、狼人剪影）
    include_yellow_bg: 是否绘制黄色圆形背景
    include_yellow_mouth: 嘴巴内部是否用黄色填充（透明背景版本需要透明嘴巴）
    """
    black = (10, 10, 10, 255)
    white = (255, 255, 255, 255)
    mouth_yellow = (255, 218, 30, 255) if include_yellow_mouth else (0, 0, 0, 0)

    cx, cy = size / 2, size / 2

    # ========== 1. 圆形黄色背景（模拟参考图的同心圆渐变效果） ==========
    if include_yellow_bg:
        layers = [
            (size * 0.485, (255, 190, 0, 255)),
            (size * 0.42,  (255, 200, 10, 255)),
            (size * 0.35,  (255, 210, 20, 255)),
            (size * 0.28,  (255, 218, 30, 255)),
            (size * 0.20,  (255, 226, 45, 255)),
        ]
        for r, color in layers:
            draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)

    # ========== 2. 左侧三条爪痕 ==========
    def claw_curve(start_x, start_y, end_x, end_y, thickness):
        points = []
        steps = 40
        for i in range(steps + 1):
            t = i / steps
            mid_x = (start_x + end_x) / 2 + (end_y - start_y) * 0.15
            mid_y = (start_y + end_y) / 2 - (end_x - start_x) * 0.15
            x = (1-t)**2 * start_x + 2*(1-t)*t * mid_x + t**2 * end_x
            y = (1-t)**2 * start_y + 2*(1-t)*t * mid_y + t**2 * end_y
            points.append((x, y))
        line_width = thickness
        for px, py in points:
            draw.ellipse([px - line_width/2, py - line_width/2,
                         px + line_width/2, py + line_width/2], fill=black)

    claw_curve(size*0.14, size*0.22, size*0.45, size*0.70, size*0.022)
    claw_curve(size*0.21, size*0.18, size*0.52, size*0.66, size*0.022)
    claw_curve(size*0.29, size*0.15, size*0.58, size*0.63, size*0.022)

    # ========== 3. 右侧云朵（2个黑色剪影） ==========
    def draw_cloud(cloud_cx, cloud_cy, scale):
        w, h = size * 0.18 * scale, size * 0.09 * scale
        draw.ellipse([cloud_cx - w*0.5, cloud_cy - h*0.3,
                     cloud_cx + w*0.5, cloud_cy + h*0.7], fill=black)
        draw.ellipse([cloud_cx - w*0.55, cloud_cy - h*0.7,
                     cloud_cx - w*0.15, cloud_cy - h*0.1], fill=black)
        draw.ellipse([cloud_cx - w*0.3, cloud_cy - h*0.9,
                     cloud_cx + w*0.1, cloud_cy - h*0.2], fill=black)
        draw.ellipse([cloud_cx + w*0.05, cloud_cy - h*0.8,
                     cloud_cx + w*0.55, cloud_cy - h*0.1], fill=black)
        draw.ellipse([cloud_cx - w*0.45, cloud_cy + h*0.1,
                     cloud_cx + w*0.45, cloud_cy + h*0.7], fill=black)

    draw_cloud(size*0.77, size*0.28, 1.0)
    draw_cloud(size*0.86, size*0.44, 0.75)

    # ========== 4. 狼人黑色剪影（核心主体） ==========
    # 4a. 尾巴
    tail_points = [
        (size*0.38, size*0.82),
        (size*0.30, size*0.86),
        (size*0.22, size*0.92),
        (size*0.18, size*0.98),
        (size*0.20, size*1.02),
        (size*0.26, size*1.04),
        (size*0.34, size*0.98),
        (size*0.42, size*0.88),
    ]
    draw.polygon(tail_points, fill=black)
    draw.ellipse([size*0.16, size*0.95, size*0.24, size*1.03], fill=black)

    # 4b. 左腿
    left_leg = [
        (size*0.42, size*0.76), (size*0.36, size*0.78),
        (size*0.32, size*0.88), (size*0.33, size*0.98),
        (size*0.44, size*1.00), (size*0.50, size*0.88),
    ]
    draw.polygon(left_leg, fill=black)

    # 4c. 右腿
    right_leg = [
        (size*0.54, size*0.76), (size*0.55, size*0.80),
        (size*0.54, size*0.94), (size*0.53, size*1.01),
        (size*0.65, size*1.00), (size*0.66, size*0.88),
        (size*0.62, size*0.78),
    ]
    draw.polygon(right_leg, fill=black)

    # 4d. 脚掌
    draw.ellipse([size*0.31, size*0.97, size*0.48, size*1.04], fill=black)
    draw.ellipse([size*0.53, size*0.97, size*0.68, size*1.04], fill=black)

    # 4e. 身体躯干
    body_w, body_h = size * 0.30, size * 0.32
    body_cx, body_cy = size * 0.50, size * 0.60
    draw.ellipse([body_cx - body_w/2, body_cy - body_h/2,
                 body_cx + body_w/2, body_cy + body_h/2], fill=black)
    draw.ellipse([size*0.36, size*0.44, size*0.66, size*0.56], fill=black)

    # 4f. 左臂
    left_arm = [
        (size*0.38, size*0.48), (size*0.30, size*0.50),
        (size*0.20, size*0.56), (size*0.12, size*0.65),
        (size*0.10, size*0.72), (size*0.15, size*0.78),
        (size*0.22, size*0.74), (size*0.28, size*0.66),
        (size*0.36, size*0.58),
    ]
    draw.polygon(left_arm, fill=black)

    # 左爪指
    claw_points_left = [
        (size*0.10, size*0.72), (size*0.07, size*0.78),
        (size*0.09, size*0.76), (size*0.08, size*0.82),
        (size*0.11, size*0.78), (size*0.11, size*0.85),
        (size*0.14, size*0.79), (size*0.16, size*0.84),
        (size*0.18, size*0.78), (size*0.22, size*0.74),
    ]
    draw.polygon(claw_points_left, fill=black)

    # 4g. 右臂
    right_arm = [
        (size*0.62, size*0.48), (size*0.72, size*0.52),
        (size*0.82, size*0.60), (size*0.90, size*0.72),
        (size*0.94, size*0.78), (size*0.92, size*0.85),
        (size*0.84, size*0.80), (size*0.76, size*0.70),
        (size*0.68, size*0.58),
    ]
    draw.polygon(right_arm, fill=black)

    # 右爪指
    rclaw_points = [
        (size*0.90, size*0.72), (size*0.93, size*0.80),
        (size*0.92, size*0.78), (size*0.95, size*0.83),
        (size*0.94, size*0.80), (size*0.97, size*0.87),
        (size*0.94, size*0.82), (size*0.98, size*0.90),
        (size*0.95, size*0.84), (size*0.92, size*0.85),
    ]
    draw.polygon(rclaw_points, fill=black)

    # ========== 5. 狼头（偏右，向上仰，嘴张开） ==========
    head_cx, head_cy = size * 0.57, size * 0.35

    # 头部椭圆
    draw.ellipse([head_cx - size*0.10, head_cy - size*0.12,
                 head_cx + size*0.14, head_cy + size*0.12], fill=black)

    # 头顶毛峰
    peak_points = [
        (head_cx - size*0.08, head_cy - size*0.11),
        (head_cx - size*0.06, head_cy - size*0.18),
        (head_cx - size*0.03, head_cy - size*0.14),
        (head_cx - size*0.00, head_cy - size*0.20),
        (head_cx + size*0.03, head_cy - size*0.15),
        (head_cx + size*0.07, head_cy - size*0.19),
        (head_cx + size*0.10, head_cy - size*0.13),
        (head_cx + size*0.14, head_cy - size*0.10),
    ]
    draw.polygon(peak_points, fill=black)

    # 嘴部（snout）
    snout = [
        (head_cx + size*0.08, head_cy - size*0.05),
        (head_cx + size*0.18, head_cy - size*0.08),
        (head_cx + size*0.22, head_cy - size*0.02),
        (head_cx + size*0.20, head_cy + size*0.02),
        (head_cx + size*0.18, head_cy + size*0.08),
        (head_cx + size*0.12, head_cy + size*0.10),
        (head_cx + size*0.05, head_cy + size*0.11),
        (head_cx + size*0.00, head_cy + size*0.09),
        (head_cx - size*0.02, head_cy + size*0.04),
    ]
    draw.polygon(snout, fill=black)

    # 嘴巴内部开口（黄色或透明）
    mouth_inner = [
        (head_cx + size*0.12, head_cy - size*0.02),
        (head_cx + size*0.20, head_cy + size*0.00),
        (head_cx + size*0.18, head_cy + size*0.06),
        (head_cx + size*0.10, head_cy + size*0.08),
        (head_cx + size*0.05, head_cy + size*0.05),
    ]
    draw.polygon(mouth_inner, fill=mouth_yellow)

    # 牙齿（白色）
    draw.polygon([(head_cx + size*0.14, head_cy + size*0.01),
                  (head_cx + size*0.155, head_cy + size*0.045),
                  (head_cx + size*0.17, head_cy + size*0.01)], fill=white)
    draw.polygon([(head_cx + size*0.175, head_cy + size*0.015),
                  (head_cx + size*0.19, head_cy + size*0.05),
                  (head_cx + size*0.205, head_cy + size*0.015)], fill=white)
    draw.polygon([(head_cx + size*0.13, head_cy + size*0.07),
                  (head_cx + size*0.145, head_cy + size*0.045),
                  (head_cx + size*0.16, head_cy + size*0.07)], fill=white)
    draw.polygon([(head_cx + size*0.17, head_cy + size*0.07),
                  (head_cx + size*0.185, head_cy + size*0.045),
                  (head_cx + size*0.20, head_cy + size*0.07)], fill=white)

    # 鼻子
    draw.polygon([(head_cx + size*0.20, head_cy - size*0.06),
                  (head_cx + size*0.23, head_cy - size*0.03),
                  (head_cx + size*0.20, head_cy - size*0.01)], fill=black)
    draw.ellipse([head_cx + size*0.195, head_cy - size*0.055,
                 head_cx + size*0.225, head_cy - size*0.03], fill=black)

    # ========== 6. 肩部背毛 ==========
    back_fur = [
        (size*0.48, size*0.45), (size*0.50, size*0.41),
        (size*0.53, size*0.44), (size*0.56, size*0.40),
        (size*0.60, size*0.43), (size*0.64, size*0.42),
        (size*0.67, size*0.46), (size*0.68, size*0.50),
        (size*0.48, size*0.50),
    ]
    draw.polygon(back_fur, fill=black)
    draw.ellipse([size*0.40, size*0.42, size*0.62, size*0.55], fill=black)


def draw_wolf_icon(size=512, with_background=True):
    """生成狼人图标 —— with_background=True 时包含黄色背景（完整图标）
    with_background=False 时只有黑色剪影（透明背景，用于自适应图标前景）"""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    _draw_wolf_content(draw, size,
                        include_yellow_bg=with_background,
                        include_yellow_mouth=with_background)
    return img


if __name__ == '__main__':
    import os
    base_dir = r'c:\Users\Squema-Mini\Documents\LRS\wolfcha-android\app\src\main\res'

    sizes = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }

    # 生成 mipmap PNG（完整图标，带黄色背景）
    for folder, sz in sizes.items():
        folder_path = os.path.join(base_dir, folder)
        os.makedirs(folder_path, exist_ok=True)
        icon = draw_wolf_icon(size=512, with_background=True)
        icon_resized = icon.resize((sz, sz), Image.LANCZOS)
        icon_resized.save(os.path.join(folder_path, 'ic_launcher.png'), 'PNG')
        icon_resized.save(os.path.join(folder_path, 'ic_launcher_round.png'), 'PNG')
        print(f'Saved {folder}/ic_launcher.png ({sz}x{sz})')

    # 生成 drawable 目录下的前景 PNG（仅狼人剪影，透明背景）—— 用于自适应图标
    fg_sizes = {
        'drawable-mdpi': 108,
        'drawable-hdpi': 162,
        'drawable-xhdpi': 216,
        'drawable-xxhdpi': 324,
        'drawable-xxxhdpi': 432,
    }
    for folder, sz in fg_sizes.items():
        folder_path = os.path.join(base_dir, folder)
        os.makedirs(folder_path, exist_ok=True)
        icon_fg = draw_wolf_icon(size=512, with_background=False)
        icon_fg_resized = icon_fg.resize((sz, sz), Image.LANCZOS)
        icon_fg_resized.save(os.path.join(folder_path, 'ic_launcher_foreground.png'), 'PNG')
        print(f'Saved {folder}/ic_launcher_foreground.png ({sz}x{sz})')

    # 预览图
    icon512 = draw_wolf_icon(size=512, with_background=True)
    icon512.save(os.path.join(base_dir, 'drawable', 'ic_launcher_preview.png'), 'PNG')
    print('Saved drawable/ic_launcher_preview.png (512x512)')

    print('\n=== DONE ===')
