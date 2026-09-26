# Capture identifiers only; UI text and descriptions are never printed or saved.
param([string]$AdbPath, [string]$DeviceSerial, [switch]$SelfTest)
$ErrorActionPreference = 'Stop'

function ConvertTo-InputMetadata([string]$RawHierarchy) {
    $start = $RawHierarchy.IndexOf('<?xml')
    $end = $RawHierarchy.LastIndexOf('</hierarchy>')
    if ($start -lt 0 -or $end -lt $start) { throw 'No hierarchy returned.' }
    [xml]$document = $RawHierarchy.Substring($start, $end + 12 - $start)
    $inputs = @()
    $ids = @()
    $packages = @()
    $classes = @()
    foreach ($node in $document.SelectNodes('//node')) {
        $packages += $node.GetAttribute('package')
        $classes += $node.GetAttribute('class')
        if ($node.GetAttribute('resource-id')) { $ids += $node.GetAttribute('resource-id') }
        if ($node.GetAttribute('focused') -eq 'true' -or $node.GetAttribute('class') -like '*EditText*') {
            $item = [ordered]@{}
            foreach ($field in @('package', 'class', 'resource-id', 'focused', 'password', 'bounds')) {
                $item[$field] = $node.GetAttribute($field)
            }
            $inputs += [pscustomobject]$item
        }
    }
    [pscustomobject]@{
        input_nodes = $inputs
        view_ids = @($ids | Sort-Object -Unique)
        packages = @($packages | Sort-Object -Unique)
        classes = @($classes | Group-Object | ForEach-Object {
            [pscustomobject]@{ class = $_.Name; count = $_.Count }
        })
    }
}

if ($SelfTest) {
    $fixture = '<?xml version="1.0"?><hierarchy><node class="android.widget.EditText" focused="true" resource-id="test:id/input" text="PRIVATE" content-desc="PRIVATE" /></hierarchy>'
    $result = ConvertTo-InputMetadata $fixture | ConvertTo-Json -Depth 5
    if ($result.Contains('PRIVATE') -or -not $result.Contains('test:id/input')) {
        throw 'Metadata redaction check failed.'
    }
    'Metadata redaction check passed'
} elseif ($AdbPath -and $DeviceSerial) {
    try {
        $raw = (& $AdbPath -s $DeviceSerial shell -tt uiautomator dump /dev/tty 2>$null) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw 'Capture failed.' }
        ConvertTo-InputMetadata $raw | ConvertTo-Json -Depth 5
    } catch {
        # Never display captured output or XML exception details.
        Write-Error 'Metadata capture failed. Check device connection and visible input screen.'
    }
} else {
    throw 'Provide -AdbPath and -DeviceSerial, or use -SelfTest.'
}
