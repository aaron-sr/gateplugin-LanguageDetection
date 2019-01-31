package gate.languagedetection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

	private List<String> languageFilter;

	private String inputASName;
	private String inputAnnotation;

	private String languageFeatureName;
	private String probabilityFeatureName;

	private Double threshold;
	private Boolean onlyGreatestProbabilility;

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
		if (onlyGreatestProbabilility) {
			if (probabilities.size() > 0) {
				DetectedLanguage detectedLanguage = probabilities.get(0);
				if (threshold == null || detectedLanguage.getProbability() >= threshold) {
					String language = detectedLanguage.getLocale().getLanguage();
					double probability = detectedLanguage.getProbability();
					featureMap.put(languageFeatureName, language);
					featureMap.put(probabilityFeatureName, probability);
				}
			}
		} else {
			for (DetectedLanguage detectedLanguage : probabilities) {
				if (threshold == null || detectedLanguage.getProbability() >= threshold) {
					appendLanguageToFeatureMap(featureMap, detectedLanguage.getLocale().getLanguage(),
							detectedLanguage.getProbability());
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void appendLanguageToFeatureMap(FeatureMap featureMap, String language, double probability) {
		List<String> languages = (List<String>) featureMap.get(languageFeatureName);
		if (languages != null) {
			languages.add(language);
		} else {
			languages = new ArrayList<>();
			languages.add(language);
			featureMap.put(languageFeatureName, languages);
		}

		Map<String, Double> probabilities = (Map<String, Double>) featureMap.get(probabilityFeatureName);
		if (probabilities != null) {
			probabilities.put(language, probability);
		} else {
			probabilities = new LinkedHashMap<>();
			probabilities.put(language, probability);
			featureMap.put(probabilityFeatureName, probabilities);
		}
	}

	private boolean isEmpty(String string) {
		return string == null || string.length() == 0;
	}

	public List<String> getLanguageFilter() {
		return languageFilter;
	}

	@Optional
	@CreoleParameter(comment = "Only detect following languages (ISO639-1 codes, see built-in language profiles")
	public void setLanguageFilter(List<String> languageFilter) {
		this.languageFilter = languageFilter;
	}

	public String getLanguageFeatureName() {
		return languageFeatureName;
	}

	@RunTime
	@CreoleParameter(comment = "The feature of document or annotation to store detected language names (i.e. stored as list, sorted by probabilities)", defaultValue = "lang")
	public void setLanguageFeatureName(String languageFeatureName) {
		this.languageFeatureName = languageFeatureName;
	}

	public String getProbabilityFeatureName() {
		return probabilityFeatureName;
	}

	@Optional
	@RunTime
	@CreoleParameter(comment = "The feature of document or annotation to store detected language probabilities (i.e. stored as map, key=language)", defaultValue = "lang_prob")
	public void setProbabilityFeatureName(String probabilityFeatureName) {
		this.probabilityFeatureName = probabilityFeatureName;
	}

	public String getInputASName() {
		return inputASName;
	}

	@Optional
	@RunTime
	@CreoleParameter(comment = "analyse specific annotations instead of whole document")
	public void setInputASName(String inputASName) {
		this.inputASName = inputASName;
	}

	public String getInputAnnotation() {
		return inputAnnotation;
	}

	@Optional
	@RunTime
	@CreoleParameter(comment = "analyse specific annotations instead of whole document")
	public void setInputAnnotation(String inputAnnotation) {
		this.inputAnnotation = inputAnnotation;
	}

	@Optional
	@RunTime
	@CreoleParameter(comment = "Only consider languages with probability above threshold")
	public void setThreshold(Double threshold) {
		this.threshold = threshold;
	}

	public Double getThreshold() {
		return threshold;
	}

	public Boolean getOnlyGreatestProbabilility() {
		return onlyGreatestProbabilility;
	}

	@Optional
	@RunTime
	@CreoleParameter(comment = "Only consider language with greatest probability, (i.e. languageFeatureName and probabilityFeatureName are stored as single value instead of list/map)", defaultValue = "true")
	public void setOnlyGreatestProbabilility(Boolean onlyGreatestProbablility) {
		this.onlyGreatestProbabilility = onlyGreatestProbablility;
	}

}