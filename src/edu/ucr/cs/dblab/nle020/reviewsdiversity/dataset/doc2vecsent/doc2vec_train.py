from random import shuffle 
from timeit import default_timer 
import multiprocessing

import numpy 
from gensim import utils
from gensim.models.doc2vec import LabeledSentence
from gensim.models import Doc2Vec


class LabeledLineSentence(object):
    def __init__(self, sources):
        self.sources = sources

        flipped = {}

        # make sure that keys are unique
        for key, value in sources.items():
            if value not in flipped:
                flipped[value] = [key]
            else:
                raise Exception('Non-unique prefix encountered')

    def __iter__(self):
        for source, prefix in self.sources.items():
            with utils.smart_open(source) as fin:
                for item_no, line in enumerate(fin):
                    yield LabeledSentence(utils.to_unicode(line).split(),
                                          [prefix + '_%s' % item_no])

    def to_array(self):
        self.sentences = []
        for source, prefix in self.sources.items():
            with utils.smart_open(source) as fin:
                for item_no, line in enumerate(fin):
                    self.sentences.append(LabeledSentence(
                        utils.to_unicode(line).split(),
                        [prefix + '_%s' % item_no]))
        return self.sentences

    def sentences_perm(self):
        numpy.random.shuffle(self.sentences)

review_dir = "./reviews/"
sources = {
        review_dir + 'train-one-star-reviews.txt':'TRAIN_ONE_STAR',
        review_dir + 'train-two-star-reviews.txt':'TRAIN_TWO_STAR',
        review_dir + 'train-three-star-reviews.txt':'TRAIN_THREE_STAR',
        review_dir + 'train-four-star-reviews.txt':'TRAIN_FOUR_STAR'}
sentences = LabeledLineSentence(sources)

# Setup doc2vec models 
start_time = default_timer()
num_dim = 400
cores = multiprocessing.cpu_count()
name_to_models = {
        "dm_concat": Doc2Vec(dm=1, dm_concat=1, size=num_dim, window=10,
            sample=1e-4, hs=0, negative=5, min_count=2, workers=cores)
#        "dm_mean": Doc2Vec(dm=1, dm_mean=1, size=num_dim, window=10,
#            sample=1e-4, hs=0, negative=5, min_count=2, workers=cores),
#        "dbow": Doc2Vec(dm=0, size=num_dim, window=10,
#            sample=1e-4, hs=0, negative=5, min_count=2, workers=cores)
        }

name_to_models["dm_concat"].build_vocab(sentences.to_array())
#for name, model in name_to_models.iteritems():
#    if name != "dbow":
#        model.reset_from(name_to_models["dbow"])
print "Time to setup model's dataset: {} s".format(default_timer() - start_time)

# Train doc2vec models
num_epoch = 10
start_time = default_timer()
previous_epoch_time = default_timer()
for name, model in name_to_models.iteritems():
    print "Training doc2vec using model {} . . .".format(name)
    for epoch in range(num_epoch):
    	sentences.sentences_perm()
    	model.train(sentences.sentences) 
        current_epoch_time = default_timer()
        print "Model {} finished epoch {} in {} s".format(name, epoch,
                (current_epoch_time - previous_epoch_time))
        previous_epoch_time = current_epoch_time

    print model.most_similar(positive=["great"])
    print "{} time: {} s".format(name, default_timer() - start_time)
    model.save(review_dir + "model." + name)
