package edu.sfsu.csc780.chathub.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.Calendar;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;
import edu.sfsu.csc780.chathub.ChatHubApplication;
import edu.sfsu.csc780.chathub.model.ChatMessage;
import edu.sfsu.csc780.chathub.R;

public class MessageUtil{

    public static final String MESSAGES_CHILD = "messages";
    private static final String LOG_TAG = MessageUtil.class.getSimpleName();
    private static DatabaseReference sFirebaseDatabaseReference = FirebaseDatabase.getInstance()
            .getReference();

    private static MessageLoadListener sAdapterListener;
    private static FirebaseAuth sFirebaseAuth;
    private static FirebaseStorage sStorage = FirebaseStorage.getInstance();

    public static FirebaseRecyclerAdapter<ChatMessage, MessageUtil.MessageViewHolder>
    getFirebaseAdapter(final Context context,
                       final Activity activity,
                       MessageLoadListener listener,
                       final LinearLayoutManager linearManager,
                       final RecyclerView recyclerView) {

        sAdapterListener = listener;

        final FirebaseRecyclerAdapter adapter = new FirebaseRecyclerAdapter<ChatMessage,
                MessageViewHolder>(
                ChatMessage.class,
                R.layout.item_message,
                MessageViewHolder.class,
                sFirebaseDatabaseReference.child(MESSAGES_CHILD)) {

            @Override
            public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                MessageViewHolder messageViewHolder  = super.onCreateViewHolder(parent, viewType);
                messageViewHolder.setMessageLoadAdapterListener(sAdapterListener);
                return messageViewHolder;

            }

            @Override
            protected void populateViewHolder(final MessageViewHolder viewHolder,
                                              ChatMessage chatMessage, int position) {

                sAdapterListener.onLoadComplete();
                final String textMessage = ChatHubApplication.getEncryptionHelper()
                        .decrypt(chatMessage.getText());
                viewHolder.messageTextView.setText(textMessage);
                viewHolder.mTextToSpeech.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        TextToSpeech ttsobj = ChatHubApplication.getChatHubApplication()
                                .getsTextToSpeechObj();
                        if(Build.VERSION.SDK_INT >= 21){
                            ttsobj.speak(textMessage,TextToSpeech.QUEUE_FLUSH,null,null);
                        }
                        else{
                            ttsobj.speak(textMessage,TextToSpeech.QUEUE_FLUSH,null);
                        }

                    }

                });

                viewHolder.messengerTextView.setText(chatMessage.getName());
                if (chatMessage.getPhotoUrl() == null) {
                    viewHolder.messengerImageView.setImageDrawable(ContextCompat
                            .getDrawable(context, R.drawable.ic_account_circle_black_36dp));

                } else {
                    SimpleTarget target = new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap bitmap, GlideAnimation glideAnimation) {
                            viewHolder.messengerImageView.setImageBitmap(bitmap);
                        }
                    };

                    if(activity != null){
                        Glide.with(activity)
                                .load(chatMessage.getPhotoUrl())
                                .asBitmap()
                                .into(target);

                    }
                    else {

                        Glide.with(context)
                                .load(chatMessage.getPhotoUrl())
                                .asBitmap()
                                .into(target);
                    }

                }
                if (chatMessage.getImageUrl() != null) {
                    //Set view visibilities for a image message
                    viewHolder.messageImageView.setVisibility(View.VISIBLE);
                    viewHolder.messageTextView.setVisibility(View.GONE);
                    // load image for message
                    try {
                        final StorageReference gsReference =
                                sStorage.getReferenceFromUrl(chatMessage.getImageUrl());
                        gsReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri uri) {
                                if(activity != null){
                                    Glide.with(activity)
                                            .load(uri)
                                            .into(viewHolder.messageImageView);
                                }
                                else{
                                    Glide.with(context)
                                            .load(uri)
                                            .into(viewHolder.messageImageView);
                                }
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception exception) {
                                Log.e(LOG_TAG, "Could not load image for message", exception);
                            }
                        });
                    } catch (IllegalArgumentException e) {
                        viewHolder.messageTextView.setText(R.string.loading_error);
                        Log.e(LOG_TAG, e.getMessage() + " : " + chatMessage.getImageUrl());
                    }
                }
                else {
                    //Set view visibilities for a text message
                    viewHolder.messageImageView.setVisibility(View.GONE);
                    viewHolder.messageTextView.setVisibility(View.VISIBLE);
                }
                if(chatMessage.getAnimateBackgroundHeart()){
                    viewHolder.messageInformTextView.setVisibility(View.VISIBLE);
                }
                sAdapterListener.animateBackground(chatMessage.getAnimateBackgroundHeart());
            }
        };

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int messageCount = adapter.getItemCount();
                int lastVisiblePosition = linearManager.findLastCompletelyVisibleItemPosition();
                if (lastVisiblePosition == -1 || (positionStart >= (messageCount - 1) && lastVisiblePosition == (positionStart - 1))) {

                    recyclerView.scrollToPosition(positionStart);

                }
            }
        });
        return adapter;

    }

    public static void send(ChatMessage chatMessage) {

        sFirebaseDatabaseReference.child(MESSAGES_CHILD).push().setValue(chatMessage);

    }

    public interface MessageLoadListener {
        void onLoadComplete();
        void animateBackground(boolean bool);

    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {

        public TextView messageTextView;
        public TextView messengerTextView;
        public CircleImageView messengerImageView;
        public ImageView messageImageView;
        public TextView messageInformTextView;
        public MessageLoadListener messageLoadAdapterListener;
        public ImageButton mTextToSpeech;

        public MessageViewHolder(View v) {
            super(v);
            messageTextView =  itemView.findViewById(R.id.messageTextView);
            messengerTextView = itemView.findViewById(R.id.messengerTextView);
            messengerImageView =itemView.findViewById(R.id.messengerImageView);
            messageImageView = itemView.findViewById(R.id.messageImageView);
            messageInformTextView = itemView.findViewById(R.id.inform_effect);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(messageInformTextView.getVisibility() == View.VISIBLE){
                        messageLoadAdapterListener.animateBackground(true);
                    }
                }
            });

            mTextToSpeech = itemView.findViewById(R.id.text_to_speech);
        }

        public void setMessageLoadAdapterListener(MessageLoadListener messageLoadAdapterListener) {
            this.messageLoadAdapterListener = messageLoadAdapterListener;
        }
    }

    public static StorageReference getImageStorageReference(FirebaseUser user, Uri uri) {
        //Create a blob storage reference with path : bucket/userId/timeMs/filename
        long nowMs = Calendar.getInstance().getTimeInMillis();
        return sStorage.getReference()
                .child(user.getUid() + "/" + nowMs + "/" + uri
                .getLastPathSegment());
    }
}
