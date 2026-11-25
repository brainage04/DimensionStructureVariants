#!/usr/bin/env bash
# Joins the five pictures of one percent sheet into a labelled row.
# usage: tools/percent_sheet.sh KIND MODE OUT.png TITLE   (reads KIND-MODE-1..5.png from the current directory)
# MODE overview crops the empty sky off each panel; closeup keeps the whole picture.
set -euo pipefail
kind=$1 mode=$2 out=$3 title=$4
font=${DSV_FONT:-$(fc-match -f '%{file}' 'DejaVu Sans:bold')}
case $mode in
  overview) shape='crop=1920:780:0:300,scale=768:-1' ;;
  closeup) shape='scale=768:432' ;;
  *) echo "MODE must be overview or closeup" >&2; exit 2 ;;
esac
inputs=() filter=""
for n in 1 2 3 4 5; do
  inputs+=(-i "$kind-$mode-$n.png")
  filter+="[$((n - 1)):v]$shape,drawbox=x=0:y=0:w=iw:h=44:color=black@0.7:t=fill,drawtext=fontfile=$font:text='$n percent':fontcolor=white:fontsize=36:x=12:y=4[p$n];"
done
filter+="[p1][p2][p3][p4][p5]hstack=inputs=5,drawbox=x=0:y=h-34:w=iw:h=34:color=black@0.7:t=fill,drawtext=fontfile=$font:text='$title':fontcolor=white:fontsize=24:x=12:y=h-30[o]"
ffmpeg -hide_banner -loglevel error -y "${inputs[@]}" -filter_complex "$filter" -map '[o]' -frames:v 1 "$out"
