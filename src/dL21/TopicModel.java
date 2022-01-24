package dL21;
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
import java.security.KeyStore.Entry;

import org.apache.commons.io.FileUtils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
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
	
	public String OUTPUT_DIR = "imgFound/"; //Make sure the folder exists before running the program
	public String INPUT_PDF = "data/3.pdf";
	public int MIN_IMAGE_SIZE = 22500; //width * height in pixels
	
	public TopicModel(){
		this.imagesDC = new ArrayList<DcImageData>();
		this.topicsDC = new ArrayList<DcTopicData>();
		this.pdf_dataDC = new DcPdfData();
		
		this.autopsyDC = new DcPdfAutopsy();
		this.autopsyDC.extracted_images = this.imagesDC;
		this.autopsyDC.extracted_topics = this.topicsDC;
		this.autopsyDC.extracted_data = this.pdf_dataDC;		
	}
	
	public class DcImageData{
		public long id;		
		public BufferedImage image;
		public int rank;
		public int width;
		public int height;
		public Long area;
		public int found_on_page_nr;		
	}
	
	public class DcTopicData{
		public ArrayList<String> keyword;
		public ArrayList<Double> weight;
		
		public Double distribution;		
	}
		
	public class DcPdfData{
		public String last_known_filesystem_path_to_pdf;
		public String title;
		public String author;	
		public String subject;
		public int total_nr_pages;
		public int total_nr_words;
		public int total_nr_graphic_objects;		
	}
	
	//Umbrella class for all extracted pdf features
	public class DcPdfAutopsy{
		public DcPdfData extracted_data;
		public ArrayList<DcTopicData> extracted_topics;
		public ArrayList<DcImageData> extracted_images;
	}
	
	public DcPdfAutopsy autopsyDC;
	public ArrayList<DcImageData> imagesDC;
	public ArrayList<DcTopicData> topicsDC;	
	public DcPdfData pdf_dataDC;	

    public static void main(String[] args) throws Exception {     	
    	System.out.println("MAIN NOT CALLED ANYMORE USE METHOD:  returnAllExtractedPdfData(String path_to_pdf_file)");    	
    }
    
    public DcPdfAutopsy returnAllExtractedPdfData(String path_to_pdf_file) throws Exception{
    	    	    	
    	System.out.println("path: " + path_to_pdf_file);
    	
    	//data structures
    	TopicModel myModel = new TopicModel();    	
    	ArrayList<String[]> top_ten_topics = new ArrayList<String[]>();
    	LinkedHashMap<BufferedImage, ArrayList<Long>> top_images = new LinkedHashMap<BufferedImage, ArrayList<Long>>();

    	myModel.INPUT_PDF = path_to_pdf_file;
    	myModel.autopsyDC.extracted_data.last_known_filesystem_path_to_pdf = path_to_pdf_file;
    	
    	//Get title, author, subject, pagenr. ...
    	myModel.getPdfMetaData();
    	
    	//Find and extract topics and images from target Pdf.
    	//Returns: ArrayList<String[]> containing [distribution, most frequentj word nr1, most frequent word nr2, most frequent word nr3, most frequent word nr4, most frequent word nr5]
    	//Consider taking the first two or three words per topic and displaying them to user (means take the first two or three words per row).
    	top_ten_topics = myModel.calcTopics();
    	
    	//Returns: LinkedHashMap<BufferedImage, ArrayList<Long>> containing [img][id,size,page]
    	//The images are already filtered by importance (size and position) and should be displayed to user
    	top_images = myModel.getImages();
    	
    	
    	//TESTING METHODS
    	//OPTIONAL: write images to disk
    	//myModel.imagesToDisk(top_images);
    	
    	//myModel.printTopicObject();
    	//myModel.printImageObject(); 
    	//myModel.printPdfMetadata();  
    	
		return myModel.autopsyDC;
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
		      thisTopicsKeywords.add(Double.toString(idCountPair.getWeight()));
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
	    	     
	    convertTopicListsToClasses(top_ten_array);
	     
        return(top_ten_array);
    }

    //Converts the top_ten_array list into the objects classes
    //Helper method for calcTopics()
    public void convertTopicListsToClasses(ArrayList<String[]> top_ten_array) {
    		    
        for (String[] entry : top_ten_array) {
        	DcTopicData next_topic = new DcTopicData();
    	    next_topic.keyword = new ArrayList<String>(); 
    	    next_topic.weight = new ArrayList<Double>();
    	    this.topicsDC.add(next_topic); 
    	    
        	next_topic.distribution = Double.parseDouble(entry[0]);
        	
        	for(int i = 1; i < entry.length; i = i+2) {
        		next_topic.keyword.add(entry[i]);
        		next_topic.weight.add(Double.parseDouble(entry[i+1]));
        	}
        }	    	   
    }
    
    //Gets important informations about the PDF like title, author, pages,...
    public void getPdfMetaData()throws IOException {
    	
    	File inFile = new File(INPUT_PDF);
	    PDDocument document = PDDocument.load(inFile);
    	
    	PDDocumentInformation info = document.getDocumentInformation();
	 	    
	    this.pdf_dataDC.title = info.getTitle();
	    this.pdf_dataDC.author = info.getAuthor();
	    this.pdf_dataDC.subject = info.getSubject();
	    this.pdf_dataDC.total_nr_pages = document.getNumberOfPages();
	    	    
	    document.close();
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
	    //System.out.println(text);
	    
	    document.close();	      	    	    	    
	    
	    //Approximate word count for pdf_dataDC
	    String words = text;	    
	    String trim = words.trim();
	    this.pdf_dataDC.total_nr_words = trim.split("\\s+").length; 	   
	    
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
    	    
        images_found = rankImages(images_found);
        
        document.close();
    	return images_found;
    }

    //Identify images from resources, write them to file and put them inside LinkedHashMap
    //Helper method for getImages()
    public LinkedHashMap<BufferedImage, ArrayList<Long>> filterResourcesForImages(PDXObject object_from_page, int page_index) throws IOException {
    	LinkedHashMap<BufferedImage, ArrayList<Long>> images_found = new LinkedHashMap<BufferedImage, ArrayList<Long>>(); 	
    	
    	//image objects are handled
    	if (object_from_page instanceof PDImageXObject) {
    		
            this.pdf_dataDC.total_nr_graphic_objects += 1;
            //add image to LinkedHashMap
            PDImageXObject image = (PDImageXObject)object_from_page;
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
        	if(form_resources == null) {
        		return images_found;
        	}
        	
            for (COSName name_inner : form_resources.getXObjectNames()) {
                PDXObject object_from_form = form_resources.getXObject(name_inner);                

                if (object_from_form instanceof PDFormXObject) {
                    //object contained in form is not an image
                } else if (object_from_form instanceof PDImageXObject) {
                	
                    this.pdf_dataDC.total_nr_graphic_objects += 1;
                	//add image to LinkedHashMap
                    PDImageXObject image = (PDImageXObject)object_from_form;
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
    
    
    //Image ranking heuristic
    //position, histogram and size
    public LinkedHashMap<BufferedImage, ArrayList<Long>> rankImages(LinkedHashMap<BufferedImage, ArrayList<Long>> images_found){
    	    	
    	LinkedHashMap<BufferedImage, ArrayList<Long>> ranked_images = new LinkedHashMap<BufferedImage, ArrayList<Long>>();
    	    	
    	//Exclude images beneath the MIN_IMAGE_SIZE
    	Iterator<BufferedImage> it = images_found.keySet().iterator();
    	
    	while(it.hasNext()) {
    		BufferedImage key = it.next();
    		
    		if(images_found.get(key).get(1) < MIN_IMAGE_SIZE) {
    			it.remove();
    		}
    	}
    	    	    	
    	
    	//TODO:
    	//**************************************************************************************************
    	//Filter images by histogram at this point of the process, this will make sure that we have filtered 
    	//out any images that are too small or dont match histogram BEFORE we take the first and last picture of the document.
    	//
    	//Container with all the images you are interested in: LinkedHashMap<BufferedImage, ArrayList<Long>> images_found
    	//The Key of the map is the "BufferedImage" these are your images
    	
    	    	
	    //Add first and last entry of images_found to the ranked_image list, because they are closest to beginning and end of document (abstract, conclusion)     	      	      	     	      
        BufferedImage[] aKeys = images_found.keySet().toArray(new BufferedImage[images_found.size()]);
        	      
	    //Fill new list with last and first entry, make sure to delete them in old list
	    if(images_found.size() > 1) {
	      	    	  	    		      	    	 
	      //System.out.println("adding first entry: " + aKeys[0] + ", " + images_found.get(aKeys[0]));
	      ranked_images.put(aKeys[0],images_found.get(aKeys[0])); 
	      images_found.remove(aKeys[0]);
	    	  
	      //System.out.println("adding last entry: " + aKeys[aKeys.length - 1] + ", " + images_found.get(aKeys[aKeys.length - 1]));
	      ranked_images.put(aKeys[aKeys.length - 1],images_found.get(aKeys[aKeys.length - 1]));
	      images_found.remove(aKeys[aKeys.length - 1]);	      
	    }	      	     
	      	      	      
	    
	    //Sort the list descending by size	      
	    List<Map.Entry<BufferedImage, ArrayList<Long>> > images_to_be_sorted = new ArrayList<Map.Entry<BufferedImage, ArrayList<Long>> >(images_found.entrySet());
	      	     
	    Collections.sort(images_to_be_sorted, new Comparator<Map.Entry<BufferedImage, ArrayList<Long>> >() {
	      // Comparing two entries by value
	      public int compare(Map.Entry<BufferedImage, ArrayList<Long>> entry1, Map.Entry<BufferedImage, ArrayList<Long>> entry2){
	   
	        // Subtracting the entries
	        return entry2.getValue().get(0).compareTo(entry1.getValue().get(0));
	      }
	    });
	      	      	      
	      
	    //Take the biggest images from the sorted list and add them to the ranked_images	       	      
	    if(images_to_be_sorted.size() > 3) {
	      for(int i = 0 ; i < 4; i++) {
	        ranked_images.put(images_to_be_sorted.get(i).getKey(), images_to_be_sorted.get(i).getValue());
	      }
	    }
	    else if(images_to_be_sorted.size() > 0) {
	      for(int i = 0 ; i < images_to_be_sorted.size(); i++) {
	        ranked_images.put(images_to_be_sorted.get(i).getKey(), images_to_be_sorted.get(i).getValue());
	      }
	    }	          	    	
    		    
	    convertRankedImagesListToClasses(ranked_images);
    	return ranked_images;
    }
    
    //Converts the ranked_images list into the objects classes
    //Helper method for getImages()
    public void convertRankedImagesListToClasses(LinkedHashMap<BufferedImage, ArrayList<Long>> ranked_images) {

    	int rank_index = 0;
    	for (BufferedImage key : ranked_images.keySet())
    	{
    	    rank_index += 1;
    	    
    	    DcImageData next_image = new DcImageData();
    		next_image.image = key;
    		next_image.id = ranked_images.get(key).get(0);
    		next_image.rank = rank_index;
    		next_image.width = key.getWidth();
    		next_image.height = key.getHeight();
    		next_image.area = ranked_images.get(key).get(1);
    		next_image.found_on_page_nr = ranked_images.get(key).get(2).intValue();    
    		this.imagesDC.add(next_image);
    	    
    	}   
    }
       
    //write the images to disk
    public void imagesToDisk(LinkedHashMap<BufferedImage, ArrayList<Long>> images_found){
    	
    	images_found.forEach((key, value) -> {
    		//System.out.println(key + ":" + value);
    		String filename = OUTPUT_DIR + value.get(0) + ".png";  
    		    		
            try {
				ImageIO.write(key, "png", new File(filename));
			} catch (IOException e) {
				System.out.println("Error saving found image to disk.");
				e.printStackTrace();
			}    		
    	});     	    	        
    }
    
    public void printImageObject() {		
		this.imagesDC.forEach(image->{        	
        	System.out.println(image.rank + ": " + image.image + ", " + image.id + ", " + image.area + ", " + image.found_on_page_nr);	
        });       
	}

	public void printTopicObject() {		
        this.topicsDC.forEach(topic->{
        	System.out.println(topic.distribution + ", " + topic.keyword.get(0) + ", " + topic.weight.get(0) + ", " + topic.keyword.get(1) + ", " + 
        	topic.weight.get(1) + ", " + topic.keyword.get(2) + ", " + topic.weight.get(2) + ", " + topic.keyword.get(3) + ", " + topic.weight.get(3) + ", "
        	+ topic.keyword.get(4) + ", " + topic.weight.get(4));
        });		     
	}
	
	public void printPdfMetadata() {
		System.out.println("Title: " + this.pdf_dataDC.title);
		System.out.println("Author: " + this.pdf_dataDC.author);
		System.out.println("Subject: " + this.pdf_dataDC.subject);
		System.out.println("Total nr. pages: " + this.pdf_dataDC.total_nr_pages);
		System.out.println("Total nr. graphic objects: " + this.pdf_dataDC.total_nr_graphic_objects);
		System.out.println("Total nr. words: " + this.pdf_dataDC.total_nr_words);
	}
    
    //print the extracted images to console
    //this method is only for testing, use the print object method instead
    public void printImages(LinkedHashMap<BufferedImage, ArrayList<Long>> images_found) {    	
    	System.out.println("\nFound images, printing map: [img][id,size,page]");
    	System.out.println("map size: " + images_found.size());
    	images_found.forEach((key, value) -> System.out.println(key + ":" + value)); 
    }

    //print the extracted topics to console
    //this method is only for testing, use the print object method instead
    public void printTopics(ArrayList<String[]> topics_found) {
	     System.out.println("\nTop ten topics: [distribution, most frequent word nr1, most frequent word nr2, most frequent word nr3, most frequent word nr4, most frequent word nr5]");
	     for(String i[] : topics_found) {
	    	 System.out.println(Arrays.toString(i));	    	 
	     }
    }    

}


