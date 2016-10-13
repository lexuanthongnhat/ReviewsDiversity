import io 
import codecs
from timeit import default_timer
from collections import OrderedDict

import numpy
from gensim.models import Doc2Vec 
from gensim.test.test_doc2vec import ConcatenatedDoc2Vec 
from gensim import utils
from sklearn.linear_model import Ridge 
from sklearn.linear_model import Lasso
from sklearn.linear_model import BayesianRidge
from sklearn.svm import LinearSVR


INFER_STEPS = 9000

# Load models
model_dir = "./reviews/models/"
model_dbow = Doc2Vec.load(model_dir + 'model.dbow')
model_dm_mean = Doc2Vec.load(model_dir + 'model.dm_mean')
model_dm_concat = Doc2Vec.load(model_dir + "model.dm_concat")

model = ConcatenatedDoc2Vec([model_dbow, model_dm_concat])
#model = model_dm_mean
#model = model_dm_concat
#model = model_dbow 


# Training set
label_to_num = OrderedDict()    # Label to the number of label's sentences
label_to_num[0] = 50000
label_to_num[1] = 50000
label_to_num[2] = 50000
label_to_num[3] = 50000 
label_to_train_tag = {
        0: "TRAIN_ONE_STAR_",
        1: "TRAIN_TWO_STAR_",
        2: "TRAIN_THREE_STAR_",
        3: "TRAIN_FOUR_STAR_"}

dimension = model.docvecs['TRAIN_ONE_STAR_0'].shape[0]
num_total = sum(label_to_num.values())
train_arrays = numpy.zeros((num_total, dimension))
train_labels = numpy.zeros(num_total)

train_index = 0
for label, num in label_to_num.iteritems():
    print "sentiment label {}, number of sentences {}".format(label, num)
    for i in range(num): 
        train_arrays[i] = model.docvecs[label_to_train_tag[label] + str(i)]
        train_labels[train_index] = label
        train_index += 1 


# Test set
start_time = default_timer()
num_sentences = 110615
test_arrays = numpy.zeros((num_sentences, dimension))
test_dir = "./reviews/all-sentences.txt"
save_file = "./reviews/all-sentences"
with codecs.open(test_dir, encoding="utf-8") as test:
    percent_to_notify = 5
    num_line_to_notify = num_sentences / (100 / percent_to_notify)

    for num, line in enumerate(test):
        line_words = utils.to_unicode(line).split()
        test_arrays[num] = model.infer_vector(line_words, steps=INFER_STEPS)

        if num % num_line_to_notify == 1:
            print "Inferred {} sentences in {} s".format(num,
                    default_timer() - start_time)

print "Infer test sentences in {} s".format(default_timer() - start_time)
#test_arrays = numpy.load(save_file + ".npy") 
print test_arrays

regression_models = { 
        "lasso": Lasso(alpha=0.1, max_iter=10000),
        "ridge": Ridge(alpha=0.1, tol=0.0001),
        "bayesian_ridge": BayesianRidge(n_iter=10000),
        "linear_svr": LinearSVR()
        }

output_dir = "../sentexp/predict/"
for name, model in regression_models.iteritems():
    start_time = default_timer() 
    print "Fitting {} . . .".format(name)
    model.fit(train_arrays, train_labels)

    with io.open(output_dir + "full_prediction_" + name + ".txt", 'w') as output:
	    output.truncate()
	    for (prediction) in model.predict(test_arrays):
		    output.write(unicode(str(prediction) + "\n")) 

    print "Running time of {} is: {} s".format(name,
                                               default_timer() - start_time) 

numpy.save(save_file, test_arrays)
