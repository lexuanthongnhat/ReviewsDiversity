# Author: Nhat Le
# Usage:
#   + gnuplot baseline_comparison.plot
#   + gnuplot -e "maindir='./resources/cellphone/eval'" baseline_comparison.plot
#     -> "maindir" should contain *.csv files
# Tested on gnuplot 5.2

set terminal pdfcairo enhanced font ",16"
#pdf terminal is deprecated
#set terminal pdf enhanced font ",16"
#set boxwidth 0.9 absolute

maindir='./'
if (!exists(ARG1)) maindir=ARG1
set print "-"
print "Gnuplot data input at dir: " . maindir

set datafile separator ","
set style data histograms
set style fill solid 0.86

# colors
#set linetype 1 lc rgb '#716e6e' # light grey
set linetype 1 lc rgb '#34495e' # light grey
set linetype 2 lc rgb '#e75957' # light red
set linetype 3 lc rgb '#369ed5' # light blue
set linetype 4 lc rgb '#77ac30' # green
set linetype 5 lc rgb '#9b59b6'
set linetype 6 lc rgb '#95a5a6'

# axes
set style line 11 lc rgb(53, 54, 55) lt 1
set border 3 front ls 11
set tics nomirror out scale 0.75
# grid
set style line 12 lc rgb '#aca7a7' lt 0 lw 1
set grid back ls 12
set grid xtics ytics mxtics

set key inside right bottom vertical Right noreverse noenhanced autotitles nobox
set style histogram clustered gap 1 title  offset character 0, 0, 0
set datafile missing '-'

set xtics border nomirror offset character 0, 0, 0 autojustify textcolor rgb 'black'
set ytics textcolor rgb 'black'
set mxtics 2

set xlabel "k"
set ylabel "Coverage (0-1)"
set xrange [1 : 22]
set ylabel "Sentiment Error"
set key inside right top vertical Right noreverse noenhanced autotitles nobox
set yrange [:] noreverse nowriteback
do for [filename in "dist_error dist_error_penalize"] {
  set output maindir . "/" . filename . ".pdf"
  plot maindir . "/" . filename . '.csv' using 1:2 title col(2) with linespoints lt 2 pointsize 1 pointtype 6 lw 3, \
    '' u 1:3 title col(3) with linespoints lt 1 pointsize 1 pointtype 1 lw 3, \
    '' u 1:4 title col(4) with linespoints lt 3 pointsize 1 pointtype 2 lw 3, \
    '' u 1:5 title col(5) with linespoints lt 4 pointsize 1 pointtype 3 lw 3, \
    '' u 1:6 title col(6) with linespoints lt 5 pointsize 1 pointtype 4 lw 3, \
    '' u 1:7 title col(7) with linespoints lt 6 pointsize 1 pointtype 8 lw 3
}

set key inside right bottom vertical Right noreverse noenhanced autotitles nobox
set yrange [0 : 1] noreverse nowriteback
set ylabel "Coverage"
do for [distance in "2 3 4"] {
	do for [sentiment in "3 5"] {
		filename = "coverage_distance" . distance . "_sentiment" . sentiment
		set output maindir . "/" . filename . ".pdf"
		plot maindir . "/" . filename . '.csv' using 1:2 title col(2) with linespoints lt 2 pointsize 1 pointtype 6 lw 3, \
			'' u 1:3 title col(3) with linespoints lt 1 pointsize 1 pointtype 1 lw 3, \
			'' u 1:4 title col(4) with linespoints lt 3 pointsize 1 pointtype 2 lw 3, \
			'' u 1:5 title col(5) with linespoints lt 4 pointsize 1 pointtype 3 lw 3, \
			'' u 1:6 title col(6) with linespoints lt 5 pointsize 1 pointtype 4 lw 3, \
			'' u 1:7 title col(7) with linespoints lt 6 pointsize 1 pointtype 8 lw 3
	}
}
