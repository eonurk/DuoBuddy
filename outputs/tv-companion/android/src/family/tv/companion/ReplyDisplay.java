package family.tv.companion;

/** Briefly presents fresh replies; controller snapshots are not conversation history UI. */
final class ReplyDisplay {
    private String id="";
    private long until=0;
    void received(String replyId,long now){id=replyId;until=now+12000;}
    void finished(long now){if(!id.isEmpty())until=now+8000;}
    void clear(){id="";until=0;}
    boolean visible(String replyId,long now,boolean speaking){
        return !id.isEmpty()&&id.equals(replyId)&&(speaking||now<until);
    }
}
