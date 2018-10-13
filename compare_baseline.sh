#!/bin/bash
# Run this script at the main directory of ReviewsDiversity project
# Usage: ./compare_baseline.sh [rebuild]

JAR="target/review-diversity-0.0.1-SNAPSHOT-jar-with-dependencies.jar"
PACKAGE="edu.ucr.cs.dblab.nle020.reviewsdiversity"

main_dir="$(pwd)/resources/cellphone"
doc_parsed_file="${main_dir}/review_transformed.jl"
doc_transformed_for_pysum="${main_dir}/review_sentences.json"
echo "$main_dir"

# make a new directory if needed
# $1 is the directory of interest
function mkdir_checked() {
  if [ ! -d $1 ]; then
    mkdir -p $1
  fi
}

sum_baselines="${main_dir}/baselines"
sum_our="${main_dir}/our"
eval_dir="${main_dir}/eval"
for new_dir in "$sum_baselines" "$sum_our" "$eval_dir"
do
  mkdir_checked "$new_dir"
done

# Rebuild jar if necessary
if [ $# -gt 0 ]; then
  if [ "$1" = "rebuild" ]; then
    mvn compile assembly:single
  elif [ "$1" = "clean-rebuild" ]; then
    mvn clean compile assembly:single
  fi
fi

##################################################
### python summarizers: textrank, lexrank, lsa
##################################################
java -cp "$JAR" "${PACKAGE}.baseline.TextSummarizer" \
    --pre-process-dataset-for-python \
    --doc-parsed-file "$doc_parsed_file" \
    --output "$doc_transformed_for_pysum"

pysum_dir="${main_dir}/pysum"
mkdir_checked "$pysum_dir"

cd src/edu/ucr/cs/dblab/nle020/reviewsdiversity/baseline/
for summarizer in "textrank" "lexrank" "lsa"
do
 pipenv run python textsum.py \
     --dataset-path "$doc_transformed_for_pysum" \
     --output-dir "$pysum_dir" "$summarizer"
done

cd -
pwd
java -cp "$JAR" "${PACKAGE}.baseline.TextSummarizer" \
    --doc-parsed-file "$doc_parsed_file" \
    --python-summary-dir "$pysum_dir" \
    --output "$sum_baselines"

##################################################
### Frequency based Java summarizers
##################################################
java -cp "$JAR" "${PACKAGE}.baseline.FreqBasedTopSets" \
    --input-file "$doc_parsed_file" --output-dir "$sum_baselines"

##################################################
### Our methods
##################################################
java -cp "$JAR" "${PACKAGE}.TopPairsProgram" \
    --type sentence \
    --summarize-only greedy_set \
    --input-file "$doc_parsed_file" \
    --output-dir "$sum_our"

##################################################
### Finally, compare all, get some plots
##################################################
java -cp "$JAR" "${PACKAGE}.baseline.BaselineComparison" \
    --doc-parsed-file "$doc_parsed_file" \
    --output "$eval_dir" \
    --our-summary-dir "$sum_our" \
    --baseline-summary-dir "$sum_baselines"

gnuplot -c resources/comparison.plt "${eval_dir}"
