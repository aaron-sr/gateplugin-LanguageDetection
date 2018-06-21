package gate.languagedetection;

import java.io.IOException;
import java.util.List;

import com.optimaize.langdetect.DetectedLanguage;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;

import gate.Annotation;
import gate.AnnotationSet;
import gate.FeatureMap;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;

/**
 * This class is the implementation of the resource LanguageDetection.
 */
@CreoleResource(name = "LanguageDetection", comment = "Integrate optimaize/language-detector (https://github.com/optimaize/language-detector) as a Processing Resource")
public class LanguageDetection extends AbstractLanguageAnalyser {

	private static final long serialVersionUID = 4531104124991700665L;

	private static final String DETECTEDLANGUAGE_SPLIT = ", ";
	private static final String PROBABILITY_SPLIT = ":";

	private List<String> languageFilter;

	private String featureName;
	private String inputASName;
	private String inputAnnotation;

	private Double threshold;

	private LanguageDetector detector;

	@Override
	public Resource init() throws ResourceInstantiationException {
		try {
			LanguageProfileReader profileReader = new LanguageProfileReader();
			List<LanguageProfile> languageProfiles;
			if (languageFilter == null || languageFilter.isEmpty()) {
				languageProfiles = profileReader.readAllBuiltIn();
			} else {
				languageProfiles = profileReader.read(languageFilter);
			}
			detector = LanguageDetectorBuilder.create(NgramExtractors.standard()).withProfiles(languageProfiles)
					.build();
		} catch (IllegalStateException | IOException e) {
			throw new ResourceInstantiationException(e);
		}
		return this;
	}

	@Override
	public void reInit() throws ResourceInstantiationException {
		init();
	}

	@Override
	public void execute() throws ExecutionException {
		try {
			if (isEmpty(inputASName) && isEmpty(inputAnnotation)) {
				String text = document.getContent().toString();
				FeatureMap featureMap = document.getFeatures();
				detectLanguage(text, featureMap);
			} else {
				AnnotationSet inputAnnotationSet = document.getAnnotations(inputASName);
				if (!isEmpty(inputAnnotation)) {
					inputAnnotationSet = inputAnnotationSet.get(inputAnnotation);
				}
				for (Annotation annotation : inputAnnotationSet) {
					String text = document.getContent()
							.getContent(annotation.getStartNode().getOffset(), annotation.getEndNode().getOffset())
							.toString();
					FeatureMap featureMap = annotation.getFeatures();
					detectLanguage(text, featureMap);
				}
			}
		} catch (Exception e) {
			throw new ExecutionException(e);
		}
	}

	private void detectLanguage(String text, FeatureMap featureMap) {
		List<DetectedLanguage> probabilities = detector.getProbabilities(text);
		for (DetectedLanguage detectedLanguage : probabilities) {
			if (threshold == null || detectedLanguage.getProbability() >= threshold) {
				appendLanguageToFeatureMap(featureMap, detectedLanguage.getLocale().getLanguage(),
						detectedLanguage.getProbability());
			}
		}
	}

	private void appendLanguageToFeatureMap(FeatureMap featureMap, String language, double probability) {
		Object object = document.getFeatures().get(featureName);
		if (object != null) {
			featureMap.put(featureName, object.toString() + DETECTEDLANGUAGE_SPLIT + asString(language, probability));
		} else {
			featureMap.put(featureName, asString(language, probability));
		}
	}

	private String asString(String language, double probability) {
		return language + PROBABILITY_SPLIT + probability;
	}

	private boolean isEmpty(String string) {
		return string == null || string.length() == 0;
	}

	public List<String> getLanguageFilter() {
		return languageFilter;
	}

	@Optional
	@CreoleParameter(comment = "Only detect following languages")
	public void setLanguageFilter(List<String> languageFilter) {
		this.languageFilter = languageFilter;
	}

	public String getFeatureName() {
		return featureName;
	}

	@RunTime
	@CreoleParameter(comment = "Name of the feature to store detected language, document or annotation", defaultValue = "lang")
	public void setFeatureName(String featureName) {
		this.featureName = featureName;
	}

	public String getInputASName() {
		return inputASName;
	}

	@Optional
	@RunTime
	@CreoleParameter(comment = "analyse specific annotation instead of whole document")
	public void setInputASName(String inputASName) {
		this.inputASName = inputASName;
	}

	public String getInputAnnotation() {
		return inputAnnotation;
	}

	@Optional
	@RunTime
	@CreoleParameter(comment = "analyse specific annotation instead of whole document")
	public void setInputAnnotation(String inputAnnotation) {
		this.inputAnnotation = inputAnnotation;
	}

	@Optional
	@RunTime
	@CreoleParameter(comment = "Only annotate languages with threshold")
	public void setThreshold(Double threshold) {
		this.threshold = threshold;
	}

	public Double getThreshold() {
		return threshold;
	}

}