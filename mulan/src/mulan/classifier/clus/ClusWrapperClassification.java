/*
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package mulan.classifier.clus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import mulan.classifier.InvalidDataException;
import mulan.classifier.MultiLabelLearnerBase;
import mulan.classifier.MultiLabelOutput;
import mulan.data.MultiLabelInstances;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.TechnicalInformation;
import weka.filters.Filter;
import weka.filters.unsupervised.instance.SparseToNonSparse;

/**
 * This class implements a wrapper for the multi-label classification methods included in <a
 * href="https://dtai.cs.kuleuven.be/clus/">CLUS</a> library.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * @version 2013.04.01
 * 
 */
public class ClusWrapperClassification extends MultiLabelLearnerBase {

    private static final long serialVersionUID = 1L;
    /**
     * The directory where all temporary files needed or generated by CLUS library are written.
     */
    protected String clusWorkingDir;
    /**
     * The dataset name that will be used for training, test and settings files.
     */
    protected String datasetName;
    /**
     * Path to the settings file.
     */
    protected String settingsFilePath;
    /**
     * Whether an ensemble method will be used.
     */
    protected boolean isEnsemble = false;
    /**
     * Whether a rule-based method will be used.
     */
    protected boolean isRuleBased = false;

    /**
     * Constructor with 2 arguments. This constructor is used when the settings file that is required by CLUS
     * will be created inside the class.
     * 
     * @param clusWorkingDir the working directory for clus
     * @param datasetName the name of the dataset
     */
    public ClusWrapperClassification(String clusWorkingDir, String datasetName) {
        this.clusWorkingDir = clusWorkingDir;
        this.datasetName = datasetName;
    }

    /**
     * Constructor with 3 arguments. This constructor is used when an existing settings file will be used.
     * 
     * @param clusWorkingDir the working directory for clus
     * @param datasetName the name of the dataset
     * @param settingsFilePath the path for settings file
     */
    public ClusWrapperClassification(String clusWorkingDir, String datasetName, String settingsFilePath) {
        this.clusWorkingDir = clusWorkingDir;
        this.datasetName = datasetName;
        this.settingsFilePath = settingsFilePath;
    }

    /**
     * This method does the following:
     * <ol>
     * <li>Creates a working directory for the CLUS library</li>
     * <li>Makes the supplied training set CLUS compliant and copies it to the working directory</li>
     * <li>Modifies the File, TestSet and Target lines of the settings file to the appropriate values</li>
     * </ol>
     */
    @Override
    protected void buildInternal(MultiLabelInstances trainingSet) throws Exception {
        // create the CLUS working directory if it does not exist
        File theDir = new File(clusWorkingDir);
        if (!theDir.exists()) {
            System.out.println("Creating CLUS working directory: " + clusWorkingDir);
            boolean result = theDir.mkdir();
            if (result) {
                System.out.println("CLUS working directory created");
            }
        }

        // transform the supplied MultilabelInstances object in an arff formated file (accepted by
        // CLUS) and write the file in the working directory with the appropriate name
        makeClusCompliant(trainingSet, clusWorkingDir + datasetName + "-train.arff");

        if (settingsFilePath != null) {
            // modify the File, TestSet and Target lines of the settings file to the appropriate values
            BufferedReader in = new BufferedReader(new FileReader(new File(settingsFilePath)));
            String settings = "";
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("File")) {
                    settings += "File = " + clusWorkingDir + this.datasetName + "-train.arff" + "\n";
                } else if (line.startsWith("TestSet")) {
                    settings += "TestSet = " + clusWorkingDir + this.datasetName + "-test.arff" + "\n";
                } else if (line.startsWith("Target")) {
                    settings += "Target = ";
                    for (int i = 0; i < numLabels - 1; i++) {
                        // all targets except last
                        settings += (labelIndices[i] + 1) + ",";
                    }
                    // last target
                    settings += (labelIndices[numLabels - 1] + 1) + "\n";
                } else {
                    settings += line + "\n";
                }
            }
            in.close();

            BufferedWriter out = new BufferedWriter(new FileWriter(new File(clusWorkingDir + this.datasetName
                    + "-train.s")));
            out.write(settings);
            out.close();
        }
    }

    /**
     * This method exists so that CLUSWrapperClassification can extend MultiLabelLearnerBase. Also helps the
     * Evaluator to determine the type of the MultiLabelOutput and thus prepare the appropriate evaluation
     * measures.
     */
    @Override
    protected MultiLabelOutput makePredictionInternal(Instance instance) throws Exception,
            InvalidDataException {
        double[] confidences = new double[numLabels];
        return new MultiLabelOutput(confidences, 0.5);

    }

    /**
     * Takes a dataset as a MultiLabelInstances object and writes an arff file that is compliant with CLUS.
     * 
     * @param mlDataset the dataset as a MultiLabelInstances object
     * @param fileName the name of the generated arff file
     * @throws Exception Potential exception thrown. To be handled in an upper level.
     */
    public static void makeClusCompliant(MultiLabelInstances mlDataset, String fileName) throws Exception {
        BufferedWriter out = new BufferedWriter(new FileWriter(new File(fileName)));

        // the file will be written in the datasetPath directory
        // Instances dataset = mlDataset.getDataSet();
        // any changes are applied to a copy of the original dataset
        Instances dataset = new Instances(mlDataset.getDataSet());
        SparseToNonSparse stns = new SparseToNonSparse(); // new instance of filter
        stns.setInputFormat(dataset); // inform filter about dataset **AFTER** setting options
        Instances nonSparseDataset = Filter.useFilter(dataset, stns); // apply filter

        String header = new Instances(nonSparseDataset, 0).toString();
        // preprocess the header
        // remove ; characters and truncate long attribute names
        String[] headerLines = header.split("\n");
        for (int i = 0; i < headerLines.length; i++) {
            if (headerLines[i].startsWith("@attribute")) {
                headerLines[i] = headerLines[i].replaceAll(";", "SEMI_COLON");
                String originalAttributeName = headerLines[i].split(" ")[1];
                String newAttributeName = originalAttributeName;
                if (originalAttributeName.length() > 30) {
                    newAttributeName = originalAttributeName.substring(0, 30) + "..";
                }
                out.write(headerLines[i].replace(originalAttributeName, newAttributeName) + "\n");
            } else {
                out.write(headerLines[i] + "\n");
            }
        }
        for (int i = 0; i < nonSparseDataset.numInstances(); i++) {
            if (i % 100 == 0) {
                out.flush();
            }
            out.write(nonSparseDataset.instance(i) + "\n");
        }
        out.close();
    }

    public String getClusWorkingDir() {
        return clusWorkingDir;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public boolean isEnsemble() {
        return isEnsemble;
    }

    public void setEnsemble(boolean isEnsemble) {
        this.isEnsemble = isEnsemble;
    }

    public boolean isRuleBased() {
        return isRuleBased;
    }

    public void setRuleBased(boolean isRuleBased) {
        this.isRuleBased = isRuleBased;
    }

    @Override
    public TechnicalInformation getTechnicalInformation() {
        // TODO Add Technical Information!
        return null;
    }
}