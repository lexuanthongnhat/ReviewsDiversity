# Nhat : gnuplot survey.plot
#set terminal pdfcairo enhanced font "Helvetica,10"
set terminal pdf enhanced font ",16"
#set boxwidth 0.9 absolute

set datafile separator ","
set style data histograms
set style fill solid 0.86

# colors
set linetype 1 lc rgb '#716e6e' # light grey
set linetype 2 lc rgb '#e75957' # light red
set linetype 3 lc rgb '#369ed5' # light blue

# axes
set style line 11 lc rgb(53, 54, 55) lt 1
set border 3 front ls 11
set tics nomirror out scale 0.75
# grid
set style line 12 lc rgb '#aca7a7' lt 0 lw 1
set grid back ls 12
set grid xtics ytics mxtics

set key inside right top vertical Right noreverse noenhanced autotitles nobox
set style histogram clustered gap 1 title  offset character 0, 0, 0
set datafile missing '-'

set xtics border nomirror offset character 0, 0, 0 autojustify textcolor rgb 'black'
set ytics textcolor rgb 'black'
set mxtics 2

set xlabel "Number of Pairs"
set ylabel "Average Time (ms)"
#set xrange [1 : 22]
#set yrange [0 : 1] noreverse nowriteback

do for [folder in "'Top Pairs/' 'Top REVIEW/' 'Top SENTENCE/'"] {
	do for [k in "3 5 10 15 20"] {
		do for [sentiment in "1 3"] {
			set title "k=" . k . ", sentiment threshold = 0." . sentiment
			subfolder='k' . k . "_threshold0." . sentiment . "/"
			filename = "time_" . 'k' . k . "_s" . sentiment
			set output folder . subfolder . filename . ".pdf"
			plot folder . subfolder . filename . '.csv' using 1:2 title column with lines, \
				'' u 1:3 title column with lines, \
				'' u 1:4 title column with lines
				
			set output folder . subfolder . "setup_" . filename . ".pdf"
			plot folder . subfolder . filename . '.csv' using 1:5 title column with lines, \
				'' u 1:6 title column with lines, \
				'' u 1:7 title column with lines, \
		
			set output folder . subfolder . "main_" . filename . ".pdf"
			plot folder . subfolder . filename . '.csv' using 1:8 title column with lines, \
				'' u 1:9 title column with lines, \
				'' u 1:10 title column with lines
		}
	}
}