import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch
import numpy as np

# ─────────────────────────────────────────────────────────────────────────────
# 辅助函数 (Helpers)
# ─────────────────────────────────────────────────────────────────────────────

def rounded_box(ax, x, y, w, h, color, text, fontsize=9, text_color="white",
                radius=0.04, zorder=3, bold=False, alpha=1.0):
    """绘制带圆角的矩形框并添加文字"""
    box = FancyBboxPatch((x - w/2, y - h/2), w, h,
                         boxstyle=f"round,pad=0.01,rounding_size={radius}",
                         linewidth=0, facecolor=color, zorder=zorder, alpha=alpha)
    ax.add_patch(box)
    weight = "bold" if bold else "normal"
    ax.text(x, y, text, ha="center", va="center", fontsize=fontsize,
            color=text_color, fontweight=weight, zorder=zorder+1,
            wrap=True)

def arrow(ax, x0, y0, x1, y1, color="#555555", lw=1.5, zorder=2,
          label=None, label_color="#333333", style="->", label_size=7):
    """绘制箭头并可选添加标签"""
    ax.annotate("", xy=(x1, y1), xytext=(x0, y0),
                arrowprops=dict(arrowstyle=style, color=color,
                                lw=lw, connectionstyle="arc3,rad=0.0"),
                zorder=zorder)
    if label:
        mx, my = (x0+x1)/2, (y0+y1)/2
        ax.text(mx, my, label, ha="center", va="center",
                fontsize=label_size, color=label_color,
                bbox=dict(boxstyle="round,pad=0.15", facecolor="white",
                          edgecolor="none", alpha=0.85),
                zorder=zorder+1)

# ─────────────────────────────────────────────────────────────────────────────
# 核心配置与坐标计算
# ─────────────────────────────────────────────────────────────────────────────

# 设定等间距的 X 轴坐标 (Phase 1 到 Phase 6)
x_coords = [1.4, 3.5, 5.6, 7.7, 9.8, 11.9]

fig1, ax1 = plt.subplots(figsize=(15, 9))
ax1.set_xlim(0, 14)
ax1.set_ylim(0, 9)
ax1.axis("off")
fig1.patch.set_facecolor("#F0F4F8")
ax1.set_facecolor("#F0F4F8")

# 标题
ax1.text(7, 8.6, "Runtime2Test — Full Pipeline", ha="center", va="center",
         fontsize=16, fontweight="bold", color="#1A202C")

# 绘制背景阶段带 (Bands)
bands = [
    (x_coords[0], "#E3F2FD", "Phase 1\nPrepare"),
    (x_coords[1], "#EDE7F6", "Phase 2\nInstrument & Run"),
    (x_coords[2], "#E8F5E9", "Phase 3\nCapture"),
    (x_coords[3], "#FFF3E0", "Phase 4\nGenerate"),
    (x_coords[4], "#ECEFF1", "Phase 5\nLLM Service"),
    (x_coords[5], "#FCE4EC", "Phase 6\nPost-Process"),
]

for xc, col, lbl in bands:
    rect = FancyBboxPatch((xc - 1.0, 0.3), 2.0, 7.8,
                          boxstyle="round,pad=0.05",
                          facecolor=col, edgecolor="none",
                          linewidth=0, alpha=0.55, zorder=0)
    ax1.add_patch(rect)
    ax1.text(xc, 8.0, lbl, ha="center", va="center", fontsize=7.5,
             color="#555", fontweight="bold")

# ─────────────────────────────────────────────────────────────────────────────
# 绘制各阶段方框与内部箭头
# ─────────────────────────────────────────────────────────────────────────────

# --- Phase 1: Prepare ---
rounded_box(ax1, x_coords[0], 7.0, 1.7, 0.55, "#1565C0", "Main.java\n(CLI Entry)", fontsize=8, bold=True)
rounded_box(ax1, x_coords[0], 5.9, 1.7, 0.5,  "#1E88E5", "Config.json\n+ Methods.json", fontsize=7.5)
rounded_box(ax1, x_coords[0], 4.8, 1.7, 0.5,  "#1E88E5", "Prepare\n(Instrument Setup)", fontsize=7.5)
arrow(ax1, x_coords[0], 6.72, x_coords[0], 6.15, label="load")
arrow(ax1, x_coords[0], 5.65, x_coords[0], 5.05, label="init")

# --- Phase 2: Instrumentation ---
rounded_box(ax1, x_coords[1], 7.0, 1.7, 0.55, "#4527A0", "Java Agent\n(Instrumentation)", fontsize=8, bold=True)
rounded_box(ax1, x_coords[1], 5.9, 1.7, 0.5,  "#7E57C2", "Production\nWorkload", fontsize=7.5)
rounded_box(ax1, x_coords[1], 4.8, 1.7, 0.5,  "#7E57C2", "Tracing\nContextHolder", fontsize=7.5)
arrow(ax1, x_coords[0]+0.85, 7.0, x_coords[1]-0.85, 7.0, label="attach")
arrow(ax1, x_coords[1], 6.72, x_coords[1], 6.15, label="run")
arrow(ax1, x_coords[1], 5.65, x_coords[1], 5.05, label="intercept")

# --- Phase 3: Capture ---
rounded_box(ax1, x_coords[2], 7.0, 1.7, 0.55, "#1B5E20", "Event Capture\n(Invocations)", fontsize=8, bold=True)
rounded_box(ax1, x_coords[2], 5.9, 1.7, 0.5,  "#43A047", "DataReader\n(Load from disk)", fontsize=7.5)
rounded_box(ax1, x_coords[2], 4.8, 1.7, 0.5,  "#43A047", "RuntimeFacts\nBuilder", fontsize=7.5)
arrow(ax1, x_coords[1]+0.85, 7.0, x_coords[2]-0.85, 7.0, label="record")
arrow(ax1, x_coords[2], 6.72, x_coords[2], 6.15, label="persist")
arrow(ax1, x_coords[2], 5.65, x_coords[2], 5.05, label="aggregate")

# --- Phase 4: Generation ---
rounded_box(ax1, x_coords[3], 7.0, 1.75, 0.55, "#E65100", "Generation.java", fontsize=8, bold=True)
rounded_box(ax1, x_coords[3], 5.9, 1.7, 0.5, "#FF7043", "LlmClient", fontsize=7.5)
arrow(ax1, x_coords[2]+0.85, 7.0, x_coords[3]-0.875, 7.0)
arrow(ax1, x_coords[3], 6.72, x_coords[3], 6.15, label="build prompt")

# --- Phase 5: LLM Service (坐标与尺寸调整) ---
rounded_box(ax1, x_coords[4], 5.9, 1.7, 0.5, "#37474F", "LLM Endpoint", fontsize=7.5, bold=True)
rounded_box(ax1, x_coords[4], 4.8, 1.7, 0.5, "#455A64", "Generated Files", fontsize=7.5)
# 横向 Request 箭头
arrow(ax1, x_coords[3]+0.85, 5.9, x_coords[4]-0.85, 5.9, color="#FF7043", label="request", label_size=6.5)
# 纵向 Response 箭头
arrow(ax1, x_coords[4], 5.65, x_coords[4], 5.05, label="LlmTestResponse", color="#888")

# --- Phase 6: Post-Process & Write (坐标与垂直流调整) ---
rounded_box(ax1, x_coords[5], 5.9, 1.7, 0.55, "#880E4F", "PostProcessor", fontsize=8, bold=True)
rounded_box(ax1, x_coords[5], 4.8, 1.7, 0.5, "#C2185B", "Optimize imports\nRemove redundant code", fontsize=7)
rounded_box(ax1, x_coords[5], 3.7, 1.7, 0.55, "#AD1457", "Write to files", fontsize=8)

# 顶部主流程触发线 (Generation -> PostProcessor)
arrow(ax1, x_coords[3]+0.875, 7.0, x_coords[5], 7.0, color="#555", style="-") # 水平段
arrow(ax1, x_coords[5], 7.0, x_coords[5], 6.175, color="#555", label="trigger") # 垂直落下

# Phase 6 内部垂直箭头
arrow(ax1, x_coords[5], 5.625, x_coords[5], 5.05)
arrow(ax1, x_coords[5], 4.55, x_coords[5], 3.975)

# ─────────────────────────────────────────────────────────────────────────────
# 图例与保存
# ─────────────────────────────────────────────────────────────────────────────

legend_items = [
    ("#1565C0", "CLI / Config"), ("#4527A0", "Instrumentation"),
    ("#1B5E20", "Capture"), ("#E65100", "Generation"),
    ("#37474F", "LLM Service"), ("#880E4F", "Post-Process"),
]
for i, (c, lbl) in enumerate(legend_items):
    px = 0.3 + i * 2.3
    ax1.add_patch(FancyBboxPatch((px, 0.35), 0.28, 0.28, boxstyle="round,pad=0.02", facecolor=c, edgecolor="none"))
    ax1.text(px + 0.35, 0.49, lbl, va="center", fontsize=7, color="#333")

plt.tight_layout(pad=0.2)
# 请确保该路径在你的机器上存在，或者改为 "./pipeline_diagram.png"
save_path = "pipeline_diagram.png"
fig1.savefig(save_path, dpi=180, bbox_inches="tight", facecolor=fig1.get_facecolor())
plt.close(fig1)

print(f"Diagram successfully saved to: {save_path}")