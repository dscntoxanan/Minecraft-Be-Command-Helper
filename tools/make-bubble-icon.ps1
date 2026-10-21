# 把原版应用图标做成圆形 PNG，供面板标题栏与悬浮球共用
#
# 为什么做成资源而不是运行时代码裁切：
#   悬浮球是代码建的 ImageView，可以用 outlineProvider 裁；
#   但布局里的 bubbleIcon 是 XML 里的 View，没法直接圆形裁切。
#   两边要长得一样，最省事的做法是生成一份**预先裁好的圆形 PNG**资源。
#
# 输入：WSX 提取出的原版图标各密度 PNG（.tmp/xero/icon/）
# 输出：app/src/main/res/mipmap-<密度>/ic_bubble_round.png

param(
    [string]$IconDir = 'D:\Agent\Deepseek\.tmp\xero\icon',
    [string]$ResDir  = 'D:\Agent\Deepseek\CommandAssistant\app\src\main\res'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$densities = @{
    'ldpi'    = 'ic_launcher_ldpi.png'
    'mdpi'    = 'ic_launcher_mdpi.png'
    'hdpi'    = 'ic_launcher_hdpi.png'
    'xhdpi'   = 'ic_launcher_xhdpi.png'
    'xxhdpi'  = 'ic_launcher_xxhdpi.png'
    'xxxhdpi' = 'ic_launcher_xxxhdpi.png'
}

foreach ($d in $densities.Keys) {
    $src = Join-Path $IconDir $densities[$d]
    if (-not (Test-Path $src)) { throw "缺少图标: $src" }

    $outDir = Join-Path $ResDir "mipmap-$d"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $dst = Join-Path $outDir 'ic_bubble_round.png'

    $srcImg = [System.Drawing.Image]::FromFile($src)
    try {
        $size = $srcImg.Width   # 图标是正方形
        $bmp = New-Object System.Drawing.Bitmap($size, $size)
        try {
            $g = [System.Drawing.Graphics]::FromImage($bmp)
            try {
                # 抗锯齿：不做的话圆边会有锯齿，在游戏画面上很显眼
                $g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
                $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $g.Clear([System.Drawing.Color]::Transparent)

                $path = New-Object System.Drawing.Drawing2D.GraphicsPath
                try {
                    $path.AddEllipse(0, 0, $size, $size)
                    $g.SetClip($path)
                    $g.DrawImage($srcImg, 0, 0, $size, $size)
                } finally { $path.Dispose() }
            } finally { $g.Dispose() }

            $bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $bmp.Dispose() }
    } finally { $srcImg.Dispose() }

    Write-Host ("  {0,-8} {1,3}x{1,-3} -> {2}" -f $d, $size, $dst)
}

Write-Host '完成'
