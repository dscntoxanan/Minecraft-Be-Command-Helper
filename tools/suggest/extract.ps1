param(
    [string]$LangPath = 'D:\Agent\Deepseek\.tmp\mc-lang\zh_CN.lang',
    [string]$OutDir   = 'D:\Agent\Deepseek\.tmp\mc-lang'
)
# Extract official Chinese names from the vanilla zh_CN.lang and cross-check the
# IDs against the texture pack, then dump CSVs for review.
#
# ASCII-only source on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI unless a
# BOM is present, so a non-ASCII literal here would be mangled into a parse error.
# The texture pack filename is therefore built from code points.

$ErrorActionPreference = 'Stop'

$packName = 'Be' + [char]0x539F + [char]0x7248 + [char]0x6750 + [char]0x8D28 + [char]0x5305 + '.zip'
$packPath = Join-Path 'D:\Agent\Deepseek\WSX' $packName

$lines = [System.IO.File]::ReadAllLines($LangPath, [System.Text.Encoding]::UTF8)

$item = @{}
$tile = @{}
$entity = @{}
foreach ($line in $lines) {
    if ($line -notmatch '=') { continue }
    $idx = $line.IndexOf('=')
    $key = $line.Substring(0, $idx).Trim()
    $val = $line.Substring($idx + 1).Trim()
    if ($val -eq '') { continue }
    if ($key -match '^item\.(.+)\.name$') { $item[$Matches[1]] = $val }
    elseif ($key -match '^tile\.(.+)\.name$') { $tile[$Matches[1]] = $val }
    elseif ($key -match '^entity\.(.+)\.name$') { $entity[$Matches[1]] = $val }
}

Write-Output "lang: item=$($item.Count) tile=$($tile.Count) entity=$($entity.Count)"

Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($packPath)
$packItems = New-Object System.Collections.Generic.HashSet[string]
$packBlocks = New-Object System.Collections.Generic.HashSet[string]
foreach ($e in $z.Entries) {
    $n = $e.FullName
    if ($n -match '^vanilla/textures/items/([^/]+)\.png$') { [void]$packItems.Add($Matches[1]) }
    elseif ($n -match '^vanilla/textures/blocks/([^/]+)\.png$') { [void]$packBlocks.Add($Matches[1]) }
}
$z.Dispose()
Write-Output "pack: items=$($packItems.Count) blocks=$($packBlocks.Count)"

# Junk filter: things that are useless or confusing in a /give suggestion list
# (spawn eggs, boats, beds, signs, banners, smithing templates ...).
$junk = 'spawn_egg|_egg$|^boat$|_boat$|_raft$|^bed$|_bed$|_sign$|_hanging_sign$|banner_pattern|_banner$|_trim|_smithing_template|_pottery_sherd|^potion$|_potion$|^tipped_arrow$|record_|_music_disc|axolotl|^pale_|^light_block|_bundle$|^bundle'

$itemRows = @()
foreach ($id in ($item.Keys | Sort-Object)) {
    if ($id -match $junk) { continue }
    if ($id -match '\.') { continue }
    if ($id -cmatch '[A-Z]') { continue }   # synthetic lang keys (tropical fish colours) are not real ids
    $itemRows += [pscustomobject]@{ id = $id; name = $item[$id]; inPack = $packItems.Contains($id) }
}
$blockRows = @()
foreach ($id in ($tile.Keys | Sort-Object)) {
    if ($id -match $junk) { continue }
    if ($id -match '\.') { continue }
    if ($id -cmatch '[A-Z]') { continue }   # synthetic lang keys (tropical fish colours) are not real ids
    $blockRows += [pscustomobject]@{ id = $id; name = $tile[$id]; inPack = $packBlocks.Contains($id) }
}
$entityRows = @()
foreach ($id in ($entity.Keys | Sort-Object)) {
    if ($id -match '\.') { continue }
    if ($id -cmatch '[A-Z]') { continue }   # synthetic lang keys (tropical fish colours) are not real ids
    $entityRows += [pscustomobject]@{ id = $id; name = $entity[$id]; inPack = $true }
}

$itemRows   | Export-Csv "$OutDir\items.csv"    -NoTypeInformation -Encoding UTF8
$blockRows  | Export-Csv "$OutDir\blocks.csv"   -NoTypeInformation -Encoding UTF8
$entityRows | Export-Csv "$OutDir\entities.csv" -NoTypeInformation -Encoding UTF8

$inPackItems = ($itemRows | Where-Object inPack).Count
$inPackBlocks = ($blockRows | Where-Object inPack).Count
Write-Output "after filter: items=$($itemRows.Count) inPack=$inPackItems ; blocks=$($blockRows.Count) inPack=$inPackBlocks ; entities=$($entityRows.Count)"
Write-Output '--- sample items that exist in the pack ---'
$itemRows | Where-Object inPack | Select-Object -First 15 id, name | Format-Table -AutoSize | Out-String -Width 100
Write-Output '--- sample entities ---'
$entityRows | Select-Object -First 12 id, name | Format-Table -AutoSize | Out-String -Width 100
