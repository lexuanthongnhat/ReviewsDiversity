# Nhat : gnuplot survey.plot
#set terminal pdfcairo enhanced font "Helvetica,10"
set terminal pdf enhanced font ",16"
#set boxwidth 0.9 absolute

set datafile separator ","
set style data histograms
set style fill solid 0.86

# colors
set style line 1 lt 1 lc rgb '#5d5959' # light grey
set style line 2 lt 1 lc rgb '#2394d0' # light blue
set style line 3 lt 1 lc rgb '#df3c3a' # light red

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

set ylabel "Average Time (ms)"
#set xrange [1 : 22]
#set yrange [0 : 1] noreverse nowriteback
do for [xaxis in "0 1"] {
	if (xaxis == 0) {
		set xlabel "Number of Pairs"
		filenameprefix = "time_"
	} else {
		set xlabel "Number of Pairs + Edges"
		filenameprefix = "time_pair_edge_"
	}
	do for [folder in "'Top Pairs/' 'Top REVIEW/' 'Top SENTENCE/'"] {
	#	do for [k in "3 5 10 15 20"] {
		do for [k in "10 15"] {
			do for [sentiment in "1 3"] {
				set title "k=" . k . ", sentiment threshold = 0." . sentiment							
				
				filename = filenameprefix . 'k' . k . "_s" . sentiment
				subfolder='k' . k . "_threshold0." . sentiment . "/"				
				set output folder . subfolder . filename . ".pdf"
				plot folder . subfolder . filename . '.csv' using 1:2 title column with lines ls 1, \
					'' u 1:3 title column with lines ls 2, \
					'' u 1:4 title column with lines ls 3

				set output folder . subfolder . "setup_" . filename . ".pdf"
				plot folder . subfolder . filename . '.csv' using 1:5 title column with lines ls 1, \
					'' u 1:6 title column with lines ls 2, \
					'' u 1:7 title column with lines ls 3

				set output folder . subfolder . "main_" . filename . ".pdf"
				plot folder . subfolder . filename . '.csv' using 1:8 title column with lines ls 1, \
					'' u 1:9 title column with lines ls 2, \
					'' u 1:10 title column with lines ls 3
#					'' u 1:11 title column with lines, \
#					'' u 1:12 title column with lines
			}
		}
	}
}