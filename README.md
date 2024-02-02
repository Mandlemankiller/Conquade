![image](https://raw.githubusercontent.com/Mandlemankiller/Conquade/master/branding/banner.png)

# Conquade video player

A video player designed to play videos in the linux console.<br>

## Requirements

- Terminal that supports [Truecolor ANSI](https://en.wikipedia.org/wiki/Color_depth#True_color_(24-bit))
- [Java 17](https://www.oracle.com/java/technologies/downloads/)

## Known problems

- Video lags and sometimes desyncs with audio
- Videos that contain raw audio larger than 2.1GB can't be played

## Showcase

![image](https://raw.githubusercontent.com/Mandlemankiller/Conquade/master/branding/render.png)
![image](https://raw.githubusercontent.com/Mandlemankiller/Conquade/master/branding/rick.gif)
![image](https://raw.githubusercontent.com/Mandlemankiller/Conquade/master/branding/cars.gif#)

## Usage

If you want to view usage the terminal-like way,
go [here](https://raw.githubusercontent.com/Mandlemankiller/Conquade/master/src/main/resources/usage.txt).

Required parameters are marked with *. <br><br>
`conquade.jar <action> <params>`

#### Global args

- `-debug` &emsp; enable debug (verbose) mode
- `-tmp` &emsp; set the conquade temporary folder (default: "/tmp/conquade" | "%USER%\AppData\Local\Temp\conquade")
- `-ffmpeg` &emsp; set path to the ffmpeg executable (default: "ffmpeg")

### Help

`conquade.jar help <args>` &emsp; print usage information

### Render

`conquade.jar render <args>` &emsp; render a video file to a conquade file

#### Render args

- *`-i` &emsp; input video file path
- *`-o` &emsp; output conquade file path (should end with .cqd file extension)
- `-fps <number>` &emsp; set the rendering fps (default: 30), it must be true that "0 < fps < 256"
- `-force` &emsp; overwrite output file if it already exists
- `-noaudio` &emsp; do not render audio, even if it is present (must be used for videos without an audio track)

### Play

`conquade.jar play <args>` &emsp; play a prerendered conquade file

#### Play args

- *`-i` &emsp; input conquade file path
- `-noaudio` &emsp; do not play audio, even if it is present
- `-nores` &emsp; ignore resolution mismatch (when the rendered video size is different from the terminal size)

### Stream

`conquade.jar stream <args>` &emsp; stream (play) a video without prerendering it

#### Stream args

- *`-i` &emsp; input video file path
- `-fps` &emsp; set the streaming fps (default: 30), it must be true that "0 < fps < 256"
- `-noaudio` &emsp; do not stream audio, even if it is present (must be used for videos without an audio track)

## Examples

- Render a video at 50 FPS: <br>
  `java -jar conquade.jar render -i ~/Videos/shrek.mp4 -o ~/Videos/shrek.cqd -fps 50`
- Play a prerendered video: <br>
  `java -jar conquade.jar play -i ~/Videos/shrek.cqd`
- Play a prerendered video without sound: <br>
  `java -jar conquade.jar play -i ~/Videos/shrek.cqd -noaudio`
- Stream a video at 50 FPS with a different ffmpeg executable:  <br>
  `java -jar conquade.jar stream -i ~/Videos/shrek.mp4 -fps 50 -ffmpeg /opt/ffmpeg-6.1.1/ffmpeg`

## Build

Requires [Maven](https://maven.apache.org/download.cgi)

```bash
git clone https://github.com/Mandlemankiller/Conquade.git
cd Conquade
mvn package
```

The jar file is located in `Conquade/target/Conquade-1.0-SNAPSHOT-jar-with-dependencies.jar`