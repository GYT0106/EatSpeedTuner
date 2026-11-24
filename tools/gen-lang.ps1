param(
    [string]$DataFile = (Join-Path $PSScriptRoot 'lang-data.json'),
    [string]$OutDir   = (Join-Path $PSScriptRoot '..\src\main\resources\assets\eatspeedtuner\lang')
)

# Expands tools/lang-data.json into one Minecraft lang file per locale.
# Every locale gets exactly the same key set, so a missing key can never
# silently fall back to English on just one language.

$ErrorActionPreference = 'Stop'

$data = Get-Content -LiteralPath $DataFile -Raw -Encoding UTF8 | ConvertFrom-Json

$keyMap = [ordered]@{
    'screen.title'     = 'eatspeedtuner.screen.title'
    'screen.input'     = 'eatspeedtuner.screen.input'
    'screen.apply'     = 'eatspeedtuner.screen.apply'
    'screen.reset'     = 'eatspeedtuner.screen.reset'
    'screen.refresh'   = 'eatspeedtuner.screen.refresh'
    'screen.hint'      = 'eatspeedtuner.screen.hint'
    'screen.invalid'   = 'eatspeedtuner.screen.invalid'
    'screen.preview'   = 'eatspeedtuner.screen.preview'
    'screen.current'   = 'eatspeedtuner.screen.current'
    'source_local'     = 'eatspeedtuner.screen.source_local'
    'source_remote'    = 'eatspeedtuner.screen.source_remote'
    'screen.sent'      = 'eatspeedtuner.screen.sent'
    'screen.applied'   = 'eatspeedtuner.screen.applied'
    'screen.refreshed' = 'eatspeedtuner.screen.refreshed'
    'not_allowed'      = 'eatspeedtuner.message.not_allowed'
    'command.query'    = 'eatspeedtuner.command.query'
    'command.set'      = 'eatspeedtuner.command.set'
    'key.open'         = 'key.eatspeedtuner.open_config'
    'key.category'     = 'key.categories.eatspeedtuner'
}

$utf8 = [System.Text.UTF8Encoding]::new($false)
$count = 0

foreach ($locale in $data.PSObject.Properties.Name) {
    $table = $data.$locale
    $obj = [ordered]@{}
    foreach ($src in $keyMap.Keys) {
        $value = $table.$src
        if ($null -eq $value -or "$value" -eq '') {
            throw "Locale '$locale' is missing key '$src'"
        }
        $obj[$keyMap[$src]] = [string]$value
    }
    $json = $obj | ConvertTo-Json -Depth 3
    $path = Join-Path $OutDir "$locale.json"
    [System.IO.File]::WriteAllText($path, $json, $utf8)
    Write-Host "wrote $path"
    $count++
}

Write-Host "generated $count locale file(s)"
