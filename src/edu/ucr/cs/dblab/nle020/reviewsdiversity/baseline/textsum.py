import argparse
import json
import logging
import os
from gensim.summarization import summarize as gensim_summarize
from sumy.nlp.tokenizers import Tokenizer
from sumy.parsers.plaintext import PlaintextParser
from sumy.summarizers.lex_rank import LexRankSummarizer
from sumy.summarizers.lsa import LsaSummarizer
from sumy.nlp.stemmers import Stemmer
from sumy.utils import get_stop_words


logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)
ch = logging.StreamHandler()
ch.setFormatter(logging.Formatter('%(asctime)s-%(levelname)s - %(message)s'))
logger.addHandler(ch)


DATASET_PATH = "sentence_data.json"
K_LIST = [3, 5, 10, 15, 20]
LANGUAGE = "english"
TEXT_SUMMARIZER = {
        "textrank": summarize_wth_textrank,
        "lexrank": summarize_wth_lexrank,
        "lsa": summarize_wth_lsa
        }
SUMY_SUMMARIZER = {
        "lexrank": LexRankSummarizer,
        "lsa": LsaSummarizer
        }


def summarize_reviews(engine, dataset_path, output_dir):
    """Summarize reviews using a specific engine.

    Args:
        engine: str, a key in TEXT_SUMMARIZER
        dataset_path: str, path to the json file of (doctor -> reviews)
        output_dir: str, directory to export summaries
    """
    if engine not in TEXT_SUMMARIZER:
        logger.warn("Couldn't find engine: {}".format(engine))
    doc_to_sentence_wth_id = load_dataset(dataset_path)
    k_to_doc_sum = {}
    for k in K_LIST:
        doc_to_sum_sentence_ids = {}
        for doc, sentence_wth_id in doc_to_sentence_wth_id.items():
            sum_sentences = TEXT_SUMMARIZER[engine](sentence_wth_id.keys(), k)
            doc_to_sum_sentence_ids[doc] = _retrieve_sentence_ids(
                    sum_sentences, sentence_wth_id)
        k_to_doc_sum[k] = doc_to_sum_sentence_ids

    filepaths = export_summary_in_json(k_to_doc_sum, engine, output_dir)
    logger.info("Exported summaries to {}".format(filepaths))


def _retrieve_sentence_ids(sum_sentences, sentence_wth_id):
    """Special retrieval when summarizer broke sentences differently.

    E.g., LexRank in summy break down "!!!" into 2 sentences.
    """
    sentence_ids = []
    for s in sum_sentences:
        if s in sentence_wth_id:
            sentence_ids.append(sentence_wth_id[s])
        else:
            sentence_id = None
            for sentence, sentence_id in sentence_wth_id.items():
                if sentence.startswith(s):
                    sentence_id = sentence_wth_id[sentence]
                    break
            if sentence_id is not None:
                sentence_ids.append(sentence_id)
            else:
                logger.error('missing sentence: "{}"'.format(s))
    return sentence_ids


def summarize_wth_lexrank(sentences, summary_len):
    return summarize_wth_sumy(SUMY_SUMMARIZER["lexrank"],
                              sentences, summary_len)


def summarize_wth_lsa(sentences, summary_len):
    return summarize_wth_sumy(SUMY_SUMMARIZER["lsa"],
                              sentences, summary_len)


def summarize_wth_sumy(sumy_summarizer, sentences, summary_len):
    """Summarize text using Sumy summarizer.

    Args:
        summarizer: sumy summarizer such as LexRankSummarizer, LsaSummarizer
        sentences: list, list of sentences to summarize
        summary_len: int, number of sentences in summary
    Return:
        tuple of sentences
    """
    parser = PlaintextParser.from_string(" ".join(sentences),
                                         Tokenizer(LANGUAGE))
    stemmer = Stemmer(LANGUAGE)

    summarizer = sumy_summarizer(stemmer)
    summarizer.stop_words = get_stop_words(LANGUAGE)
    sum_sentences = [sentence._text
                     for sentence in summarizer(parser.document, summary_len)]
    return sum_sentences


def summarize_wth_textrank(sentences, summary_len):
    """Summarize text using textrank algorithm

    Args:
        sentences: list, list of sentences to summarize
        summary_len: int, number of sentences in summary
    Return:
        tuple of sentences
    """
    ratio = float(summary_len) / len(sentences)
    sentence_str = " ".join(sentences)
    sum_sentences = []

    current_len = 0
    trial = 0
    while current_len != summary_len:
        sum_sentences = gensim_summarize(sentence_str, ratio=ratio, split=True)
        current_len = len(sum_sentences)
        if current_len != summary_len:
            if current_len < summary_len:
                ratio += 1.0 / 1000
            else:
                ratio -= 1.0 / 1000
            trial += 1
        elif trial:
            print('find {} sentences now!!!'.format(current_len))
        if ratio > 1 or trial > 1000:
            break

    return sum_sentences


def load_dataset(dataset_path):
    """Load doctor's review dataset in json.

    Args:
        dataset_path: path to dataset in json file
    Returns:
        doc_to_sentence_wth_id: dict, doctor -> dict(sentence, sentence_id)
    """
    with open(dataset_path) as f:
        doc_to_sentences = json.load(f)

    doc_to_sentence_wth_id = {}
    for doc, sentences in doc_to_sentences.items():
        sentence_wth_id = {sentence: id for id, sentence in sentences.items()}
        doc_to_sentence_wth_id[doc] = sentence_wth_id
    return doc_to_sentence_wth_id


def export_summary_in_json(k_to_doc_sum, engine, output_dir):
    """Export summary to json files.

    Args:
        k_to_doc_sum: dict, k -> dict (doctor, sentence id list)
        engine: str, summarization engine - a key in TEXT_SUMMARIZER
        output_dir: str, directory to export
    Returns:
        a set of exported file paths
    """
    k_filepaths = {
            k: os.path.join(output_dir, "{}_{}.json".format(engine, int(k)))
            for k, doc_to_sentences in k_to_doc_sum.items()
            }
    for k, doc_to_sentences in k_to_doc_sum.items():
        with open(k_filepaths[k], 'w') as f:
            json.dump(doc_to_sentences, f)
    return k_filepaths.values()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="Text Summarizer")
    p_add = parser.add_argument
    p_add("engine", choices=TEXT_SUMMARIZER.keys(),
          help="Text summarization methods")
    p_add("--dataset-path", default=DATASET_PATH,
          help='Json dataset file path, default="{}"'.format(DATASET_PATH))
    p_add("--output-dir", default="./summary/",
          help='Directory to export summaries, default="{./summary/}"')
    args = parser.parse_args()
    logger.info(args)

    summarize_reviews(args.engine, args.dataset_path, args.output_dir)
