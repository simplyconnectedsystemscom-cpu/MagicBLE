$sourcePath = "c:\Users\simpl\Desktop\MagicBLE\magic_barcode_rotated.png"
$shell = New-Object -ComObject Shell.Application
$devices = $shell.NameSpace(17).Items() | Where-Object { $_.Type -eq "Portable Device" -or $_.Type -like "*Phone*" }

if ($devices.Count -eq 0) {
    Write-Host "Error: No portable device found. Make sure phone is unlocked and transfer mode is enabled."
    exit
}

$phone = $devices[0]
Write-Host "Found device: $($phone.Name)"

# Try to find target folder (Download)
$targetFolder = $null
function Find-Folder($item, $targetName) {
    if ($item.IsFolder) {
        $subItems = $item.GetFolder.Items()
        foreach ($subItem in $subItems) {
            if ($subItem.Name -eq $targetName) {
                return $subItem
            }
            # Be careful with recursion depth on MTP, it's slow. Only search top level.
        }
    }
    return $null
}

# Navigate 2 levels deep (Internal Storage -> Download)
$topLevel = $phone.GetFolder.Items()
foreach ($storage in $topLevel) {
    # Often "Internal Storage", "Phone", "SD Card"
    $downloadFolder = Find-Folder $storage "Download"
    if ($downloadFolder) {
        $targetFolder = $downloadFolder
        break
    }
    # Also look for "Downloads" (plural)
    if (-not $targetFolder) {
        $downloadFolder = Find-Folder $storage "Downloads"
        if ($downloadFolder) {
            $targetFolder = $downloadFolder
            break
        }
    }
}

if ($targetFolder) {
    Write-Host "Copying to $($targetFolder.Path)..."
    $targetFolder.GetFolder.CopyHere($sourcePath)
    Write-Host "Done!"
} else {
    Write-Host "Error: efficient transfer requires a 'Download' folder in the root of storage."
    Write-Host "Available top-level folders:"
    $topLevel | ForEach-Object { Write-Host " - $($_.Name)" }
}
