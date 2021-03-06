package com.idrnd.idvoice.utils;

import android.content.Context;

import net.idrnd.android.media.AssetsExtractor;
import net.idrnd.voicesdk.antispoof2.AntispoofEngine;
import net.idrnd.voicesdk.media.SpeechSummaryEngine;
import net.idrnd.voicesdk.verify.VerifyMethod;
import net.idrnd.voicesdk.verify.VoiceVerifyEngine;

import java.io.File;
import java.util.EnumSet;

/**
 * Singleton voice engines manager
 */
public class EngineManager {

    private File assetsDir;
    private VoiceVerifyEngine textDependentVoiceVerifyEngine = null;
    private VoiceVerifyEngine textIndependentVoiceVerifyEngine = null;
    private SpeechSummaryEngine speechSummaryEngine = null;
    private AntispoofEngine antispoofEngine = null;

    private static EngineManager instance;

    private EngineManager() {}

    public static EngineManager getInstance() {
        if (instance == null) {
            instance = new EngineManager();
        }

        return instance;
    }

    /**
     * Initializes singleton.
     * Should be called after the very first getInstance() invocation.
     */
    public void init(Context context) {
        // SDK assets (engines initialization data) are kept within the AAR,
        // so they should be extracted to the device filesystem in order to
        // make them available for engines.
        AssetsExtractor assetsExtractor = new AssetsExtractor(context);
        this.assetsDir = assetsExtractor.extractAssets();
    }

    /**
     * Returns speech summary engine (used for signal analysis)
     */
    public synchronized SpeechSummaryEngine getSpeechSummaryEngine() {
        if (speechSummaryEngine == null) {
            speechSummaryEngine = new SpeechSummaryEngine(new File(assetsDir, AssetsExtractor.SPEECH_SUMMARY_INIT_DATA_SUBPATH).getPath());
        }
        return speechSummaryEngine;
    }

    /**
     * Returns voice verification engine by {@link Prefs.VoiceTemplateType} (used for voice biometrics)
     * @param voiceTemplateType voice template type
     * @return Voice verify engine
     */
    public synchronized VoiceVerifyEngine getVerifyEngine(Prefs.VoiceTemplateType voiceTemplateType) {
        /*
         * MAP - text-dependent; small size of init data, large size of voice template
         * TI_X_2 - text-independent; large size of init data, small size of voice template
         * TI_X_2 | MAP - text-dependent with increased accuracy
         */
        switch (voiceTemplateType) {
            case TextIndependent:
                if (textIndependentVoiceVerifyEngine == null) {
                    textIndependentVoiceVerifyEngine = new VoiceVerifyEngine(
                        new File(assetsDir, AssetsExtractor.VERIFY_INIT_DATA_16K_SUBPATH).getPath(),
                        EnumSet.of(VerifyMethod.TI_X_2)
                    );
                }
                return textIndependentVoiceVerifyEngine;
            case TextDependent:
                if (textDependentVoiceVerifyEngine == null) {
                    textDependentVoiceVerifyEngine = new VoiceVerifyEngine(
                        new File(assetsDir, AssetsExtractor.VERIFY_INIT_DATA_16K_SUBPATH).getPath(),
                        EnumSet.of(VerifyMethod.MAP, VerifyMethod.TI_X_2)
                    );
                }
                return textDependentVoiceVerifyEngine;
        }
        return null;
    }

    /**
     * Returns anti-spoofing engine (used for liveness check)
     */
    public synchronized AntispoofEngine getAntispoofEngine() {
        if(antispoofEngine == null) {
            antispoofEngine = new AntispoofEngine(new File(assetsDir, AssetsExtractor.ANTISPOOF_INIT_DATA_SUBPATH).getPath());
        }
        return antispoofEngine;
    }

    /**
     * AnispoofEngine object consumes a lot of RAM. Call this method in order to clean the delete AntispoofEngine
     * object from memory when you no longer plan to use anti-spoofing in your activities.
     */
    public synchronized void releaseAntispoofEngine() {
        if (antispoofEngine != null) {
            antispoofEngine = null;
            System.gc();
        }
    }
}
