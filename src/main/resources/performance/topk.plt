# Nhat : gnuplot topk.plot
#set terminal pdfcairo enhanced font "Helvetica,10"
set terminal pdf enhanced font ",16"
#set boxwidth 0.9 absolute

set datafile separator ","
set style data histograms
set style fill solid 1

# colors
#set style line 1 lt 1 lc rgb '#008000' # green yellow
set style line 1 lt 1 lc rgb '#5d5959' # light grey
set style line 2 lt 1 lc rgb '#2394d0' # light blue
set style line 3 lt 1 lc rgb '#df3c3a' # light red

# axes
set style line 11 lc rgb(53, 54, 55) lt 1
set border 3 front ls 11
set tics nomirror out scale 0.75
# grid
set style line 12 lc rgb'#808080' lt 0 lw 1
set grid back ls 12
set grid xtics ytics mxtics

set key inside right top vertical Right noreverse noenhanced autotitles nobox
set style histogram clustered gap 1 title  offset character 0, 0, 0
set datafile missing '-'

set xtics border nomirror offset character 0, 0, 0 autojustify textcolor rgb 'black'
set xtics norangelimit
set xtics ()
set ytics textcolor rgb 'black'
set mxtics 2

pattern_ilp=1
pattern_rr=5
pattern_greedy=2

do for [experiment in "top-pair top-sentence top-review"] {
	do for [threshold in "1 3"] {
		fileprefix = experiment . '/' . experiment . '-' . threshold
		set output fileprefix . '-time.pdf'
		#set title "Top Sentences"
		set xlabel "k"
		set ylabel "Average Time (ms)"
		set yrange [ 0 : 70] noreverse nowriteback
		plot fileprefix . '.csv' using 2:xtic(1) title column ls 1 fillstyle transparent pattern pattern_ilp, \
			'' u 3 title col ls 2 fs transparent pattern pattern_rr, \
			'' u 4 ti col ls 3 fs transparent pattern pattern_greedy


		set output fileprefix . '-cost.pdf'
		set xlabel "k"
		set ylabel "Cost"
		set yrange [ 0 : 900] noreverse nowriteback
		plot fileprefix . '.csv' using 7:xtic(1) title col ls 1 fillstyle transparent pattern pattern_ilp, \
			'' u 8 title col ls 2 fs transparent pattern pattern_rr, \
			'' u 9 ti col ls 3 fs transparent pattern pattern_greedy
	}
}
