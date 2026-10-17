from pathlib import Path
p=Path('listening-update/CompanionService.java')
s=p.read_text()
s=s.replace('public static final String LISTEN="family.tv.companion.LISTEN";', 'public static final String LISTEN="family.tv.companion.LISTEN", FINISH_LISTEN="family.tv.companion.FINISH_LISTEN";')
s=s.replace('private MediaPlayer player;private AudioManager audioManager;private SpeechRecognizer recognizer;', 'private MediaPlayer player;private AudioManager audioManager;private SpeechRecognizer recognizer;\n    private final ListeningSession listenSession=new ListeningSession();')
s=s.replace('if(LISTEN.equals(action))startListening(false);', 'if(LISTEN.equals(action))startListening(false);\n        if(FINISH_LISTEN.equals(action))finishListening();')
a=s.index('    /** Listens over whatever')
b=s.index('    private void sendChat', a)
s=s[:a]+'''    /** Duplicate native-assistant and controller triggers must never stop an utterance. */
    private void startListening(boolean followUp){
        if(destroyed||listenSession.active())return;
        boolean allowed=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED&&SpeechRecognizer.isRecognitionAvailable(this);
        if(!allowed||(!visible&&!Settings.canDrawOverlays(this))){if(!followUp)startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}
        listenSession.begin(SystemClock.uptimeMillis(),followUp);
        autoListen=false;followUpListen=followUp;stopAudio();localError="";heard="";
        handler.postDelayed(listenTimeout,ListeningSession.MAX_DURATION_MS);
        beginRecognition();
    }
    private final Runnable retryListening=()->{if(!destroyed&&listenSession.active())beginRecognition();};
    private final Runnable listenTimeout=()->finishListening();
    private final Runnable finishTimeout=()->endListening("Listening timed out. Please try again.");
    private void releaseRecognizer(){
        SpeechRecognizer old=recognizer;recognizer=null;
        if(old!=null){try{old.cancel();}catch(Exception ignored){}old.destroy();}
    }
    private void finishListening(){
        if(!listenSession.active())return;
        listenSession.stop();handler.removeCallbacks(retryListening);handler.removeCallbacks(listenTimeout);
        if(recognizer==null){endListening("");return;}
        android.util.Log.i("BuddyVoice","Finishing speech input");
        try{recognizer.stopListening();handler.removeCallbacks(finishTimeout);handler.postDelayed(finishTimeout,3000);}
        catch(Exception e){endListening("Microphone unavailable. Please try again.");}
    }
    private void clearListening(){
        listenSession.close();handler.removeCallbacks(retryListening);handler.removeCallbacks(listenTimeout);handler.removeCallbacks(finishTimeout);
        releaseRecognizer();listening=false;
    }
    private void endListening(String error){
        clearListening();presence("idle");heard="";localError=error;
        if(!error.isEmpty())captionUntil=SystemClock.uptimeMillis()+6000;
        changed();
    }
    private void recognitionFailed(int attempt,int error){
        if(destroyed||!listenSession.current(attempt))return;
        boolean empty=error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
        boolean transientFailure=error==SpeechRecognizer.ERROR_RECOGNIZER_BUSY||error==SpeechRecognizer.ERROR_AUDIO||error==SpeechRecognizer.ERROR_CLIENT;
        android.util.Log.i("BuddyVoice","Speech error "+error);
        if(listenSession.retry(SystemClock.uptimeMillis(),empty,transientFailure)){
            // Invalidate this listener before cancellation can deliver stale callbacks.
            listenSession.attempt(SystemClock.uptimeMillis());releaseRecognizer();heard="";
            android.util.Log.i("BuddyVoice","Speech input ended early; retrying");
            handler.postDelayed(retryListening,400);changed();return;
        }
        endListening(followUpListen&&empty?"":empty?"I didn’t catch that. Try again.":"Microphone unavailable ("+error+"). You can type a request in Buddy.");
    }
    private void beginRecognition(){
        final int attempt=listenSession.attempt(SystemClock.uptimeMillis());
        releaseRecognizer();
        try{
            recognizer=SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle b){if(!listenSession.current(attempt)||destroyed)return;listenSession.ready(SystemClock.uptimeMillis());android.util.Log.i("BuddyVoice","Speech input ready");listening=true;presence("listening");changed();}
                public void onBeginningOfSpeech(){if(listenSession.current(attempt))android.util.Log.i("BuddyVoice","Speech started");}
                public void onRmsChanged(float r){}public void onBufferReceived(byte[] b){}public void onEvent(int e,Bundle b){}
                public void onEndOfSpeech(){if(listenSession.current(attempt))android.util.Log.i("BuddyVoice","Speech endpoint reached");}
                public void onError(int e){recognitionFailed(attempt,e);}
                public void onResults(Bundle b){
                    if(!listenSession.current(attempt)||destroyed)return;
                    ArrayList<String> results=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String text=results==null||results.isEmpty()?"":results.get(0).trim();
                    if(text.isEmpty()){recognitionFailed(attempt,SpeechRecognizer.ERROR_NO_MATCH);return;}
                    android.util.Log.i("BuddyVoice","Speech transcription received");clearListening();sendChat(text);
                }
                public void onPartialResults(Bundle b){if(!listenSession.current(attempt)||destroyed)return;ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(r!=null&&!r.isEmpty()){heard=r.get(0);changed();}}
            });
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US");i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            // These are hints: the bounded retry window also handles engines that ignore them.
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,2000L);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,1500L);
            recognizer.startListening(i);
        }catch(Exception e){recognitionFailed(attempt,SpeechRecognizer.ERROR_CLIENT);}
    }
''' +s[b:]
s=s.replace('if(recognizer!=null){recognizer.destroy();recognizer=null;}listening=false;', 'clearListening();')
p.write_text(s)
p=Path('listening-update/MainActivity.java');s=p.read_text()
s=s.replace('if(resumed&&!CompanionService.listening)listen();', 'if(resumed&&!controllerHandlesRemote()&&!CompanionService.listening)listen();')
s=s.replace('"Talk",()->listen()', '"Talk",()->{if(CompanionService.listening)startForegroundService(new Intent(this,CompanionService.class).setAction(CompanionService.FINISH_LISTEN));else listen();}')
s=s.replace('    private void listen(){', '    private boolean controllerHandlesRemote(){return CompanionService.state.optBoolean("remoteButtonEnabled")&&CompanionService.state.optBoolean("remoteButtonReady");}\n    private void listen(){')
s=s.replace('if(e.getRepeatCount()==0)listen();return true;', 'if(e.getRepeatCount()==0&&!controllerHandlesRemote())listen();return true;')
p.write_text(s)
