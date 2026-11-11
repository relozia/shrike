# Kestrel's music player helper. Reads what Windows' media flyout shows (title, artist, progress, cover) and
# prints one json line every half second. Commands come in through a file (toggle / next / prev), reading
# stdin from a pipe would block. Exits when the game process is gone. Nothing here touches the network.
param([string]$cmdFile, [string]$artFile, [int]$parentPid)

$ErrorActionPreference = 'Stop'
# the console defaults to the oem codepage, whose best fit mapping turns curly quotes in titles into plain " and
# breaks the json. utf8 out, java reads utf8
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -AssemblyName System.Drawing

$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1

function Await($op, [Type]$type) {
    $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
    $t.Wait(-1) | Out-Null
    $t.Result
}

$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IInputStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
# powershell gets the thumbnail stream as a bare COM object and can't match it to AsStreamForRead's parameter on
# its own, which threw on every song (so: never a cover). called through reflection it binds fine
$asStream = [System.IO.WindowsRuntimeStreamExtensions].GetMethod('AsStreamForRead', [Type[]]@([Windows.Storage.Streams.IInputStream]))
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$lastKey = ''
$artKey = ''

while ($true) {
    if (-not (Get-Process -Id $parentPid -ErrorAction SilentlyContinue)) { exit }
    try {
        $s = $mgr.GetCurrentSession()
        if (Test-Path $cmdFile) {
            $cmd = (Get-Content $cmdFile -Raw).Trim()
            Remove-Item $cmdFile
            if ($s) {
                switch ($cmd) {
                    'toggle' { $null = Await ($s.TryTogglePlayPauseAsync()) ([bool]) }
                    'next' { $null = Await ($s.TrySkipNextAsync()) ([bool]) }
                    'prev' { $null = Await ($s.TrySkipPreviousAsync()) ([bool]) }
                }
            }
        }
        if ($s) {
            $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $tl = $s.GetTimelineProperties()
            $pb = $s.GetPlaybackInfo()
            $key = $p.Title + '|' + $p.Artist
            if ($key -ne $lastKey) {
                $lastKey = $key
                $artKey = ''
                $art = 'none'
            } else {
                $art = ''
            }
            # apps often hand over the cover a moment after the title, so keep trying until this song has one
            if ($artKey -ne $key -and $p.Thumbnail) {
                try {
                    $rs = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                    $st = $asStream.Invoke($null, @($rs))
                    $img = [System.Drawing.Image]::FromStream($st)
                    # written next to it then moved, the game never reads a half written file
                    $img.Save($artFile + '.tmp', [System.Drawing.Imaging.ImageFormat]::Png)
                    $img.Dispose()
                    $st.Dispose()
                    Move-Item -Force ($artFile + '.tmp') $artFile
                    $artKey = $key
                    $art = 'new'
                } catch { }
            }
            $o = [ordered]@{
                title = $p.Title; artist = $p.Artist
                playing = ($pb.PlaybackStatus.ToString() -eq 'Playing')
                pos = $tl.Position.TotalSeconds; dur = $tl.EndTime.TotalSeconds
                age = ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalSeconds
                art = $art
                app = $s.SourceAppUserModelId
            }
        } else {
            $o = [ordered]@{ title = '' }
            $lastKey = ''
            $artKey = ''
        }
        [Console]::Out.WriteLine(($o | ConvertTo-Json -Compress))
    } catch {
        [Console]::Out.WriteLine('{"error":"' + ($_.Exception.Message -replace '"', '') + '"}')
    }
    [Console]::Out.Flush()
    Start-Sleep -Milliseconds 500
}
