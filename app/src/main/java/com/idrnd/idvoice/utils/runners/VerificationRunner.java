package com.idrnd.idvoice.utils.runners;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import com.idrnd.idvoice.models.AudioRecord;
import com.idrnd.idvoice.utils.EngineManager;
import com.idrnd.idvoice.utils.Prefs;

import net.idrnd.voicesdk.antispoof2.AntispoofResult;
import net.idrnd.voicesdk.verify.VerifyResult;
import net.idrnd.voicesdk.verify.VoiceTemplate;
import net.idrnd.voicesdk.verify.VoiceVerifyEngine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * VerificationRunner used to run verification and liveness check tasks in background threads
 * and return results to main thread
 */
public class VerificationRunner {

    private static String TAG = VerificationRunner.class.getSimpleName();
    private final ExecutorService executor;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean checkLiveness;

    /**
     * Constructor
     * @param checkLiveness param determines if liveness check is needed
     */
    public VerificationRunner(boolean checkLiveness) {
        this.checkLiveness = checkLiveness;

        final int numThreads = checkLiveness ? 3 : 2;

        if (Runtime.getRuntime().availableProcessors() >= numThreads) {
            executor = Executors.newFixedThreadPool(numThreads);
        } else {
            executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        }
    }

    /**
     * Interface used to get results of verification and liveness check
     */
    public interface VerifyCallback {
        void onComplete(Pair<VerifyResult, AntispoofResult> result);
    }

    /**
     * Function used to run verification and liveness check tasks in background threads and return result of work to main thread
     * @param recordObject user voice record
     * @param voiceTemplateType type of voice verification
     * @param callback used to performing some operations with verification and liveness check results in the main thread
     */
    public void execute(AudioRecord recordObject, Prefs.VoiceTemplateType voiceTemplateType, VerifyCallback callback) {
        executor.execute(() -> {
            try {
                EngineManager engineManager = EngineManager.getInstance();

                // Perform voice verification in a separate thread
                Future<VerifyResult> verificationResult = executor.submit(() -> {
                        // Get VoiceVerifyEngine by type
                        VoiceVerifyEngine verifyEngine = engineManager.getVerifyEngine(voiceTemplateType);

                        // Create voice template from verification audio. Note that this is a
                        // computationally expensive operation and can take considerable time.
                        VoiceTemplate verifyTemplate = verifyEngine.createVoiceTemplate(
                                recordObject.samples,
                                recordObject.sampleRate);

                        // Retrieve enrollment voice template from shared preferences
                        VoiceTemplate enrollTemplate = VoiceTemplate.deserialize(
                            Prefs.getInstance().getVoiceTemplate(voiceTemplateType)
                        );

                        Log.d(TAG, "Verify start in " + Thread.currentThread().getName());
                        return verifyEngine.verify(enrollTemplate, verifyTemplate);
                    }
                );

                final Pair<VerifyResult, AntispoofResult> result;

                if (checkLiveness) {
                    // Check spoofing in a separate thread
                    Future<AntispoofResult> antispoofingResult = executor.submit(() -> {
                            Log.d(TAG, "Antispoof start in " + Thread.currentThread().getName());
                            return engineManager.getAntispoofEngine().isSpoof(
                                recordObject.samples,
                                recordObject.sampleRate
                            );
                        }
                    );

                    Log.d(TAG, "wait verify and liveness results");
                    result = new Pair<>(verificationResult.get(), antispoofingResult.get());
                } else {
                    Log.d(TAG, "wait verify result");
                    result = new Pair<>(verificationResult.get(), null);
                }

                // Pass results of verification and liveness check to main thread
                handler.post(() -> callback.onComplete(result));
            } catch (Exception e) {
                Log.e(TAG, "Problem with voice verification and liveness scores", e);
            }
            finally {
                executor.shutdown();
            }
        });
    }
}
