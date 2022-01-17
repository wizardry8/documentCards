import cc.mallet.util.*;
import cc.mallet.types.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.topics.*;

import java.util.*;
import java.util.regex.*;
import java.awt.image.BufferedImage;
import java.io.*;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;



public class TopicModel {
	
	
	private static final String OUTPUT_DIR = "imgFound/"; //LR make sure the folder exists before running the program
	private static final String INPUT_PDF = "data/1.pdf";

    public static void main(String[] args) throws Exception {
    	
    	TopicModel myModel = new TopicModel();
    	myModel.calcTopics();
    	myModel.getImages();

    }
    
    //Tokenize, remove stopwords and apply LDA (Latent Dirichlet allocation) unsupervised learning algorithm to determine key topics of the targeted Pdf file (INPUT_PDF)
    public ArrayList<String[]> calcTopics() throws Exception{
        // Begin by importing documents from text to feature sequences
        ArrayList<Pipe> pipeList = new ArrayList<Pipe>();

        // Pipes: tokenize, remove stopwords, map to features
        pipeList.add( new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")) );
        pipeList.add( new TokenSequenceRemoveStopwords(new File("stoplists/en.txt"), "UTF-8", false, false, false) );
        
        pipeList.add( new TokenSequence2FeatureSequence() );

        InstanceList instances = new InstanceList (new SerialPipes(pipeList));
        
        //retrieve the text data from a pdf
        String text_from_pdf = this.getText();
        String[] text_array = new String[]{text_from_pdf};
        instances.addThruPipe(new StringArrayIterator(text_array));
                
        // Create a model with 10 topics, alpha_t = 0.01, beta_w = 0.01
        //  Note that the first parameter is passed as the sum over topics, while the second is the parameter for a single dimension of the Dirichlet prior.
        int numTopics = 10;
        ParallelTopicModel model = new ParallelTopicModel(numTopics, 1.0, 0.01);

        model.addInstances(instances);

        // Use two parallel samplers, which each look at one half the corpus and combine statistics after every iteration.
        model.setNumThreads(2);

        // Run the model for 1000-2000 iterations and stop (for testing 50 would suffice)
        model.setNumIterations(1000);
        model.estimate();

        // Show the words and topics in the first instance

        // The data alphabet maps word IDs to strings
        Alphabet dataAlphabet = instances.getDataAlphabet();

        FeatureSequence tokens = (FeatureSequence) model.getData().get(0).instance.getData();
        LabelSequence topics = model.getData().get(0).topicSequence;

        Formatter out = new Formatter(new StringBuilder(), Locale.US);
        for (int position = 0; position < tokens.getLength(); position++) {
            out.format("%s-%d ", dataAlphabet.lookupObject(tokens.getIndexAtPosition(position)), topics.getIndexAtPosition(position));
        }

        // Estimate the topic distribution of the first instance, given the current Gibbs state.
        double[] topicDistribution = model.getTopicProbabilities(0);

        // Get an array of sorted sets of word ID/count pairs
        ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
          
        /// Create a sorted array list containing highest distributed topics with keywords in descending order        
	     ArrayList<ArrayList<String>> topTopics = new ArrayList<ArrayList<String>>();
	     	     
	     for (int topic = 0; topic < numTopics; topic++) {
	            Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
	            			            
	            	ArrayList<String> thisTopicsKeywords = new ArrayList<String>();
	            	thisTopicsKeywords.add(String.valueOf(topicDistribution[topic]));
	            	
		            int rank = 0;
		            while (iterator.hasNext() && rank < 5) {
		            	
		                IDSorter idCountPair = iterator.next();
		                thisTopicsKeywords.add(dataAlphabet.lookupObject(idCountPair.getID()).toString());
		                rank++;
		                
		            }
		            if(rank > 0) {
		            	topTopics.add(thisTopicsKeywords);
		            }
	        }
	     	     
	     //sorting topTopic
	     ArrayList<String[]> top_ten_array = new ArrayList<String[]>();
	     topTopics.forEach((listInListOfLists) -> {	    	
		    	String[] sublist = new String[listInListOfLists.size()];
		    	top_ten_array.add(listInListOfLists.toArray(sublist));
		 });
    	 
	     top_ten_array.sort(Comparator.comparingDouble(a -> Double.parseDouble(a[0])));
	     Collections.reverse(top_ten_array);

	     //printing topTopics ArrayList
	     System.out.println("\nTop ten topics: [distribution, most frequent word nr1, most frequent word nr2, most frequent word nr3, most frequent word nr4, most frequent word nr5]");
	     for(String i[] : top_ten_array) {
	    	 System.out.println(Arrays.toString(i));	    	 
	     }
	    	     
        return(top_ten_array);
    }

    //Strips all text from the targeted Pdf file (INPUT_PDF)
    //Helper method for calcTopics()
    public String getText()throws IOException{

	    //Loading an existing document e.g.: File file = new File("C:/data/DL/1.pdf");
    	File inFile = new File(INPUT_PDF);
	    PDDocument document = PDDocument.load(inFile);

	    //Instantiate PDFTextStripper class
	    PDFTextStripper pdfStripper = new PDFTextStripper();

	    //Retrieving text from PDF document
	    String text = pdfStripper.getText(document);
	    System.out.println(text);

	    document.close();	      
	    
	    return text;	    	          	 
    }
    
    //Retrieves all images from the targeted Pdf file ((INPUT_PDF)
    public LinkedHashMap<BufferedImage, ArrayList<Long>> getImages() throws IOException{
    	
    	LinkedHashMap<BufferedImage, ArrayList<Long>> images_found = new LinkedHashMap<BufferedImage, ArrayList<Long>>();
    	images_found.clear();
    	
    	//get the pages of the pdf document and search each page for image objects
	    final PDDocument document = PDDocument.load(new File(INPUT_PDF));    		    	
		int page_index = 0;
        PDPageTree list = document.getPages();
        for (PDPage page : list) {
        	page_index++;
            PDResources page_resources = page.getResources();
            for (COSName name : page_resources.getXObjectNames()) {
                PDXObject object_from_page = page_resources.getXObject(name);
                images_found.putAll(filterResourcesForImages(object_from_page, page_index));
            }
        }        
    	
        //print the found images
    	System.out.println("\nFound images, printing map: [img][id,size,page]");
    	System.out.println("map size: " + images_found.size());
    	images_found.forEach((key, value) -> System.out.println(key + ":" + value)); 
    	    	
    	return images_found;
    }

    //Identify images from resources, write them to file and put them inside LinkedHashMap
    //Helper method for getImages()
    public LinkedHashMap<BufferedImage, ArrayList<Long>> filterResourcesForImages(PDXObject object_from_page, int page_index) throws IOException {
    	LinkedHashMap<BufferedImage, ArrayList<Long>> images_found = new LinkedHashMap<BufferedImage, ArrayList<Long>>(); 	
    	
    	//image objects are handled
    	if (object_from_page instanceof PDImageXObject) {
    		//write image to file
            PDImageXObject image = (PDImageXObject)object_from_page;
            String filename = OUTPUT_DIR + "image" + System.currentTimeMillis() + ".png";
            ImageIO.write(image.getImage(), "png", new File(filename));
            
            //add image to LinkedHashMap
            ArrayList<Long> image_properties = new ArrayList<Long>();
            image_properties.add(System.currentTimeMillis());
            image_properties.add((long)image.getWidth()*image.getHeight());
            image_properties.add((long)page_index);                        
            images_found.put(image.getImage(), image_properties);                        
        }
    	//form objects need one level deeper scanning
        else if(object_from_page instanceof PDFormXObject) {                    	
        	PDFormXObject form = (PDFormXObject)object_from_page;
        	PDResources form_resources = form.getResources();
            for (COSName name_inner : form_resources.getXObjectNames()) {
                PDXObject object_from_form = form_resources.getXObject(name_inner);

                if (object_from_form instanceof PDFormXObject) {
                    //object contained in form is not an image
                } else if (object_from_form instanceof PDImageXObject) {
            		//write image to file
                    PDImageXObject image = (PDImageXObject)object_from_form;
                    String filename = OUTPUT_DIR + "image" + System.currentTimeMillis() + ".png";
                    ImageIO.write(image.getImage(), "png", new File(filename));                            
                    //add image to LinkedHashMap
                    ArrayList<Long> image_properties = new ArrayList<Long>();
                    image_properties.add(System.currentTimeMillis());
                    image_properties.add((long)image.getWidth()*image.getHeight());
                    image_properties.add((long)page_index);                        
                    images_found.put(image.getImage(), image_properties);
                }
            }                    	      
        }
    	
    	return images_found;
    }

}