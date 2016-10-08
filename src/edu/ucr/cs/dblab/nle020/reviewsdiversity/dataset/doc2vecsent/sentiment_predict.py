import io 
import codecs
from timeit import default_timer

import numpy
from gensim.models import Doc2Vec 
from gensim.test.test_doc2vec import ConcatenatedDoc2Vec 
from gensim import utils
from sklearn.linear_model import Ridge 
from sklearn.linear_model import Lasso
from sklearn.linear_model import BayesianRidge
from sklearn.kernel_ridge import KernelRidge
from sklearn.svm import LinearSVC

model_dir = "./doc_reviews/"
start_time = default_timer()
model_dbow = Doc2Vec.load(model_dir + 'model.dbow')
model_dm_mean = Doc2Vec.load(model_dir + 'model.dm_mean')
model_dm_concat = Doc2Vec.load(model_dir + "model.dm_concat")
#model = ConcatenatedDoc2Vec([model_dm, model_dbow])
#model = model_dm
model = model_dm_concat
#model = model_dbow 

num_one_star = 50000
num_two_star = 50000
num_three_star = 50000
num_four_star = 50000
num_total = num_one_star + num_two_star + num_three_star + num_four_star
num_sentences = 244

# Training set
dimension = model.docvecs['TRAIN_ONE_STAR_1'].shape[0]
train_arrays = numpy.zeros((num_total, dimension))
train_labels = numpy.zeros(num_total)

for i in range(num_one_star):
    prefix_train = 'TRAIN_ONE_STAR_' + str(i)
    train_arrays[i] = model.docvecs[prefix_train]
    train_labels[i] = 0

offset = num_one_star
for i in range(num_two_star):
    prefix_train = 'TRAIN_TWO_STAR_' + str(i)
    train_arrays[offset + i] = model.docvecs[prefix_train]
    train_labels[offset + i] = 1

offset += num_two_star
for i in range(num_three_star):
    prefix_train = 'TRAIN_THREE_STAR_' + str(i)
    train_arrays[offset + i] = model.docvecs[prefix_train]
    train_labels[offset + i] = 2

offset += num_three_star
for i in range(num_four_star):
    prefix_train = 'TRAIN_FOUR_STAR_' + str(i)
    train_arrays[offset + i] = model.docvecs[prefix_train]
    train_labels[offset + i] = 3

#print train_arrays
#print train_labels

# Test set
start_time = default_timer()
test_arrays = numpy.zeros((num_sentences, dimension))
test_sentences = []

with codecs.open("./doc_reviews/test-sentences.txt", encoding="utf-8") as test:
    for num, line in enumerate(test):
        line_words = utils.to_unicode(line).split()
        test_arrays[num] = model.infer_vector(line_words, steps=1000)
print "Infer test sentences in {} s".format(default_timer() - start_time)
#print test_arrays

#test_arrays2 = numpy.zeros((num_sentences, dimension))
#for i in range(num_sentences):
#    prefix_test = 'TEST_SENTENCES_' + str(i)
#    test_arrays2[i] = model.docvecs[prefix_test]
#
##print test_arrays2
#for i in range(num_sentences):
#    print "Infer {}: {}".format(i, test_arrays[i]) 
#    print "Train {}: {}".format(i, test_arrays2[i])

regression_models = {
        "lasso": Lasso(alpha=0.1, max_iter=10000),
        "ridge": Ridge(alpha=0.1, tol=0.0001),
        "bayesian_ridge": BayesianRidge(n_iter=10000)
        #"kernel_ridge": KernelRidge(alpha=0.1)
        #"svr": LinearSVC()
        }

output_dir = "../sentexp/predict/"
for name, model in regression_models.iteritems():
    start_time = default_timer() 
    print "Fitting {} . . .".format(name)
    model.fit(train_arrays, train_labels)

    with io.open(output_dir + "prediction_" + name + ".txt", 'w') as output:
	    output.truncate()
	    for (prediction) in model.predict(test_arrays):
		    output.write(unicode(str(prediction) + "\n")) 

    print "Running time of {} is: {} s".format(name,
                                               default_timer() - start_time) 
