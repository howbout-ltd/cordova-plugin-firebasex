package org.apache.cordova.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import android.util.Log;
import android.app.Notification;
import android.text.TextUtils;
import android.content.ContentResolver;
import android.graphics.Color;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.Random;

public class FirebasePluginMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FirebasePlugin";

    static final String defaultSmallIconName = "notification_icon";
    static final String defaultLargeIconName = "notification_icon_large";


    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    @Override
    public void onNewToken(String refreshedToken) {
        try{
            super.onNewToken(refreshedToken);
            Log.d(TAG, "Refreshed token: " + refreshedToken);
            FirebasePlugin.sendToken(refreshedToken);
        }catch (Exception e){
            FirebasePlugin.handleExceptionWithoutContext(e);
        }
    }


    /**
     * Called when message is received.
     * Called IF message is a data message (i.e. NOT sent from Firebase console)
     * OR if message is a notification message (e.g. sent from Firebase console) AND app is in foreground.
     * Notification messages received while app is in background will not be processed by this method;
     * they are handled internally by the OS.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        try{
            // [START_EXCLUDE]
            // There are two types of messages data messages and notification messages. Data messages are handled
            // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
            // traditionally used with GCM. Notification messages are only received here in onMessageReceived when the app
            // is in the foreground. When the app is in the background an automatically generated notification is displayed.
            // When the user taps on the notification they are returned to the app. Messages containing both notification
            // and data payloads are treated as notification messages. The Firebase console always sends notification
            // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options
            // [END_EXCLUDE]

            // Pass the message to the receiver manager so any registered receivers can decide to handle it
            boolean wasHandled = FirebasePluginMessageReceiverManager.onMessageReceived(remoteMessage);
            if (wasHandled) {
                Log.d(TAG, "Message was handled by a registered receiver");

                // Don't process the message in this method.
                return;
            }

            if(FirebasePlugin.applicationContext == null){
                FirebasePlugin.applicationContext = this.getApplicationContext();
            }

            // TODO(developer): Handle FCM messages here.
            // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
            String messageType;
            String title = null;
            String body = null;
            String tag = null;
            String id = null;
            String sound = null;
            String vibrate = null;
            String light = null;
            String color = null;
            String icon = null;
            String channelId = null;
            String visibility = null;
            String priority = null;
            boolean foregroundNotification = false;

            Map<String, String> data = remoteMessage.getData();

            String conversationTitle = null;
            JSONArray conversationMessages = null;

            if (remoteMessage.getNotification() != null) {
                // Notification message payload
                Log.i(TAG, "Received message: notification");
                messageType = "notification";
                id = remoteMessage.getMessageId();
                RemoteMessage.Notification notification = remoteMessage.getNotification();
                title = notification.getTitle();
                body = notification.getBody();
                tag = notification.getTag();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    channelId = notification.getChannelId();
                }
                sound = notification.getSound();
                color = notification.getColor();
                icon = notification.getIcon();
            }else{
                Log.i(TAG, "Received message: data");
                messageType = "data";
            }

            if (data != null) {
                // Data message payload
                if(data.containsKey("notification_foreground")){
                    foregroundNotification = true;
                }
                if(data.containsKey("notification_title")) title = data.get("notification_title");
                if(data.containsKey("notification_body")) body = data.get("notification_body");
                if(data.containsKey("notification_tag")) tag = data.get("notification_tag");
                if(data.containsKey("notification_android_channel_id")) channelId = data.get("notification_android_channel_id");
                if(data.containsKey("notification_android_id")) id = data.get("notification_android_id");
                if(data.containsKey("notification_android_sound")) sound = data.get("notification_android_sound");
                if(data.containsKey("notification_android_vibrate")) vibrate = data.get("notification_android_vibrate");
                if(data.containsKey("notification_android_light")) light = data.get("notification_android_light"); //String containing hex ARGB color, miliseconds on, miliseconds off, example: '#FFFF00FF,1000,3000'
                if(data.containsKey("notification_android_color")) color = data.get("notification_android_color");
                if(data.containsKey("notification_android_icon")) icon = data.get("notification_android_icon");
                if(data.containsKey("notification_android_visibility")) visibility = data.get("notification_android_visibility");
                if(data.containsKey("notification_android_priority")) priority = data.get("notification_android_priority");
                if(data.containsKey("notification_conversation_title")) conversationTitle = data.get("notification_conversation_title");
                try {
                    if (data.containsKey("notification_conversation_messages")) conversationMessages = new JSONArray(data.get("notification_conversation_messages"));
                } catch (JSONException e) {}

            }

            // Process the provided ID into a numeric ID for the notification
            //  - If no ID is provided, a random integer is selected
            //  - If a numeric ID is provided, it is used directly
            //  - If a non-numeric ID is provided, its hashCode is used
            int numId;
            if (TextUtils.isEmpty(id)) {
                Random rand = new Random();
                numId = rand.nextInt(500) + 1;
            } else if (TextUtils.isDigitsOnly(id)) {
                numId = Integer.parseInt(id);
            } else {
                numId = id.hashCode();
            }

            Log.d(TAG, "From: " + remoteMessage.getFrom());
            Log.d(TAG, "Id: " + numId);
            Log.d(TAG, "Title: " + title);
            Log.d(TAG, "Body: " + body);
            Log.d(TAG, "Tag: " + tag);
            Log.d(TAG, "Sound: " + sound);
            Log.d(TAG, "Vibrate: " + vibrate);
            Log.d(TAG, "Light: " + light);
            Log.d(TAG, "Color: " + color);
            Log.d(TAG, "Icon: " + icon);
            Log.d(TAG, "Channel Id: " + channelId);
            Log.d(TAG, "Visibility: " + visibility);
            Log.d(TAG, "Priority: " + priority);


            if (!TextUtils.isEmpty(body) || !TextUtils.isEmpty(title) || (data != null && !data.isEmpty())) {
                boolean showNotification = (FirebasePlugin.inBackground() || !FirebasePlugin.hasNotificationsCallback() || foregroundNotification) && (!TextUtils.isEmpty(body) || !TextUtils.isEmpty(title));
                sendMessage(remoteMessage, data, messageType, numId, title, body, tag, showNotification, sound, vibrate, light, color, icon, channelId, priority, visibility, conversationTitle, conversationMessages);
            }
        }catch (Exception e){
            FirebasePlugin.handleExceptionWithoutContext(e);
        }
    }

    private void sendMessage(RemoteMessage remoteMessage, Map<String, String> data, String messageType, int id, String title, String body, String tag, boolean showNotification, String sound, String vibrate, String light, String color, String icon, String channelId, String priority, String visibility, String conversationTitle, JSONArray conversationMessages) {
        Log.d(TAG, "sendMessage(): messageType="+messageType+"; showNotification="+showNotification+"; id="+Integer.toString(id)+"; title="+title+"; body="+body+"; tag="+tag+"; sound="+sound+"; vibrate="+vibrate+"; light="+light+"; color="+color+"; icon="+icon+"; channel="+channelId+"; data="+data.toString());
        Bundle bundle = new Bundle();
        for (String key : data.keySet()) {
            bundle.putString(key, data.get(key));
        }
        bundle.putString("messageType", messageType);
        this.putKVInBundle("id", Integer.toString(id), bundle);
        this.putKVInBundle("title", title, bundle);
        this.putKVInBundle("body", body, bundle);
        this.putKVInBundle("tag", tag, bundle);
        this.putKVInBundle("sound", sound, bundle);
        this.putKVInBundle("vibrate", vibrate, bundle);
        this.putKVInBundle("light", light, bundle);
        this.putKVInBundle("color", color, bundle);
        this.putKVInBundle("icon", icon, bundle);
        this.putKVInBundle("channel_id", channelId, bundle);
        this.putKVInBundle("priority", priority, bundle);
        this.putKVInBundle("visibility", visibility, bundle);
        this.putKVInBundle("show_notification", String.valueOf(showNotification), bundle);
        this.putKVInBundle("from", remoteMessage.getFrom(), bundle);
        this.putKVInBundle("collapse_key", remoteMessage.getCollapseKey(), bundle);
        this.putKVInBundle("sent_time", String.valueOf(remoteMessage.getSentTime()), bundle);
        this.putKVInBundle("ttl", String.valueOf(remoteMessage.getTtl()), bundle);

        if (showNotification) {
            Intent intent = new Intent(this, OnNotificationOpenReceiver.class);
            intent.putExtras(bundle);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            // Channel
            if(channelId == null || !FirebasePlugin.channelExists(channelId)){
                channelId = FirebasePlugin.defaultChannelId;
            }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                Log.d(TAG, "Channel ID: "+channelId);
            }

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);
            notificationBuilder
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

            if (conversationMessages == null) {
                notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
            } else {

                // Process conversation messages
                NotificationCompat.MessagingStyle notificationMessage = new NotificationCompat.MessagingStyle("You");
                for (int i = 0; i < conversationMessages.length(); i++) {
                    try {
                        JSONObject messageInfo = conversationMessages.getJSONObject(i);
                        String messageContent = messageInfo.getString("message");
                        String notificationBody = messageInfo.optString("body", messageContent);
                        Long messageTime = messageInfo.getLong("timestamp");
                        Person messageSender = getMessageSender(
                            messageInfo.getJSONObject("sender").getString("id"),
                            messageInfo.getJSONObject("sender").getString("name"));
                        notificationMessage.addMessage(notificationBody, messageTime, messageSender);
                    } catch (JSONException e) {}
                }
                if (conversationTitle != null) notificationMessage.setConversationTitle(conversationTitle);
                notificationBuilder.setStyle(notificationMessage);

            }

            // On Android O+ the sound/lights/vibration are determined by the channel ID
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O){
                // Sound
                if (sound == null) {
                    Log.d(TAG, "Sound: none");
                }else if (sound.equals("default")) {
                    notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                    Log.d(TAG, "Sound: default");
                }else{
                    Uri soundPath = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/raw/" + sound);
                    Log.d(TAG, "Sound: custom=" + sound+"; path="+soundPath.toString());
                    notificationBuilder.setSound(soundPath);
                }

                // Light
                if (light != null) {
                    try {
                        String[] lightsComponents = color.replaceAll("\\s", "").split(",");
                        if (lightsComponents.length == 3) {
                            int lightArgb = Color.parseColor(lightsComponents[0]);
                            int lightOnMs = Integer.parseInt(lightsComponents[1]);
                            int lightOffMs = Integer.parseInt(lightsComponents[2]);
                            notificationBuilder.setLights(lightArgb, lightOnMs, lightOffMs);
                            Log.d(TAG, "Lights: color="+lightsComponents[0]+"; on(ms)="+lightsComponents[2]+"; off(ms)="+lightsComponents[3]);
                        }

                    } catch (Exception e) {}
                }

                // Vibrate
                if (vibrate != null){
                    try {
                        String[] sVibrations = vibrate.replaceAll("\\s", "").split(",");
                        long[] lVibrations = new long[sVibrations.length];
                        int i=0;
                        for(String sVibration: sVibrations){
                            lVibrations[i] = Integer.parseInt(sVibration.trim());
                            i++;
                        }
                        notificationBuilder.setVibrate(lVibrations);
                        Log.d(TAG, "Vibrate: "+vibrate);
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            }


            // Icon
            int defaultSmallIconResID = getResources().getIdentifier(defaultSmallIconName, "drawable", getPackageName());
            int customSmallIconResID = 0;
            if(icon != null){
                customSmallIconResID = getResources().getIdentifier(icon, "drawable", getPackageName());
            }

            if (customSmallIconResID != 0) {
                notificationBuilder.setSmallIcon(customSmallIconResID);
                Log.d(TAG, "Small icon: custom="+icon);
            }else if (defaultSmallIconResID != 0) {
                Log.d(TAG, "Small icon: default="+defaultSmallIconName);
                notificationBuilder.setSmallIcon(defaultSmallIconResID);
            } else {
                Log.d(TAG, "Small icon: application");
                notificationBuilder.setSmallIcon(getApplicationInfo().icon);
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                int defaultLargeIconResID = getResources().getIdentifier(defaultLargeIconName, "drawable", getPackageName());
                int customLargeIconResID = 0;
                if(icon != null){
                    customLargeIconResID = getResources().getIdentifier(icon+"_large", "drawable", getPackageName());
                }

                int largeIconResID;
                if (customLargeIconResID != 0 || defaultLargeIconResID != 0) {
					if (customLargeIconResID != 0) {
	                    largeIconResID = customLargeIconResID;
	                    Log.d(TAG, "Large icon: custom="+icon);
	                }else{
	                    Log.d(TAG, "Large icon: default="+defaultLargeIconName);
	                    largeIconResID = defaultLargeIconResID;
	                }
	                notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(), largeIconResID));
                }
            }

            // Color
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                int defaultColor = getResources().getColor(getResources().getIdentifier("accent", "color", getPackageName()), null);
                if(color != null){
                    notificationBuilder.setColor(Color.parseColor(color));
                    Log.d(TAG, "Color: custom="+color);
                }else{
                    Log.d(TAG, "Color: default");
                    notificationBuilder.setColor(defaultColor);
                }
            }

            // Visibility
            int iVisibility = NotificationCompat.VISIBILITY_PUBLIC;
            if(visibility != null){
                iVisibility = Integer.parseInt(visibility);
            }
            Log.d(TAG, "Visibility: " + iVisibility);
            notificationBuilder.setVisibility(iVisibility);

            // Priority
            int iPriority = NotificationCompat.PRIORITY_MAX;
            if(priority != null){
                iPriority = Integer.parseInt(priority);
            }
            Log.d(TAG, "Priority: " + iPriority);
            notificationBuilder.setPriority(iPriority);


            // Build notification
            Notification notification = notificationBuilder.build();

            // Display notification
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Log.d(TAG, "show notification: "+notification.toString());
            notificationManager.notify(tag, id, notification);
        }
        // Send to plugin
        FirebasePlugin.sendMessage(bundle, this.getApplicationContext());
    }

    private void putKVInBundle(String k, String v, Bundle b){
        if(v != null && !b.containsKey(k)){
            b.putString(k, v);
        }
    }

    private Person getMessageSender(String id, String name) {
        // Try and get an icon for the sender from the profile_pictures directory in cache
        IconCompat profilePictureIcon = null;
        Bitmap imageBitmap = BitmapFactory.decodeFile(getApplicationContext().getCacheDir().getPath().concat("/profile_pictures/").concat(id).concat(".jpg"));
        if (imageBitmap != null) {
            // Make the profile picture a circle
            profilePictureIcon = IconCompat.createWithBitmap(makeBitmapRound(imageBitmap));
        }

        return new Person.Builder()
            .setName(name)
            .setKey(id)
            .setIcon(profilePictureIcon)
            .build();
    }

    private static Bitmap makeBitmapRound(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2, bitmap.getWidth() / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

}
