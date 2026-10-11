package com.example.Deepscalp;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Image extends AppCompatActivity {
    private static final int SELECT_IMAGE = 1;
    private ImageView imageView;
    private TextView resultTextView;
    private Button selectImageButton;
    private Button cameraBtn;
    private String[] classLabels = {"양호", "경증", "중등도", "중증"}; // 클래스 라벨 정의

    private Interpreter[] tflite = new Interpreter[6]; // 모델을 배열로 선언
    private String[] modelNames = {"Baldness", "Dandruff", "Microcorn", "Seborrheic_Dermatitis", "Folliculitis_Abscess", "Folliculitis"};
    private String[] modelNames_KR = {"탈모", "비듬", "미세각질", "피지과다", "모낭사이홍반", "모낭홍반농포"};
    private TextView[] ResultTxt = new TextView[6];
    private ProgressBar[] progressBars = new ProgressBar[6];

    private String[] result_class = new String[6];
    public float[] maxresult = new float[tflite.length];
    public int maxIndex = 0;//증상 배열 인덱스 번호
    float max = maxresult[0];//최대 출력값

    static String keyword;

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private Uri photoURI = null;
    private Uri fileUri = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        imageView = findViewById(R.id.imageView);
        resultTextView = findViewById(R.id.resultTextView);
        selectImageButton = findViewById(R.id.selectImageButton);
        Button btn_camera = findViewById(R.id.btn_camera);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { //카메라 권한이 없을 경우
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1001);//카메라 권한 요청
        }

        // 각 ProgressBar를 초기화
        for (int i = 0; i < progressBars.length; i++) {
            int resID = getResources().getIdentifier("progressBar" + (i + 1), "id", getPackageName());
            progressBars[i] = findViewById(resID);
        }

        for (int i = 0; i < ResultTxt.length; i++) {
            int resID = getResources().getIdentifier("T_Result" + (i + 1), "id", getPackageName());
            ResultTxt[i] = findViewById(resID);
        }

        try {
            // 여러 모델 로드
            for (int i = 0; i < tflite.length; i++) {
                tflite[i] = new Interpreter(loadModelFile(modelNames[i] + ".tflite"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        selectImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, SELECT_IMAGE);


            }
        });


        btn_camera.setOnClickListener(view -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                    if (photoFile != null) {
                        photoURI = FileProvider.getUriForFile(this,
                                getApplicationContext().getPackageName() + ".provider",
                                photoFile);
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                    }
                } catch (IOException ex) {
                    // Error occurred while creating the File
                }
            }
        });
    }
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SELECT_IMAGE && resultCode == RESULT_OK && data != null) {
            try {
                InputStream inputStream = getContentResolver().openInputStream(data.getData());
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);//업로드 된 이미지를 비트맵으로 바꿈
                bitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);//비트맵 사이즈 조절
                imageView.setImageBitmap(bitmap);//이미지 뷰에 업로드

                saveBitmapToFirebase(bitmap);
                classifyImage(bitmap);//classifyimage 메소드에 bitmap 보내기

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Uri imageUri = photoURI; // 카메라 인텐트를 시작할 때 생성한 Uri를 직접 사용

            // URI를 비트맵으로 변환
            Bitmap bitmap2 = getBitmapFromUri(imageUri, this);


            classifyImage(bitmap2);

            saveBitmapToFirebase(bitmap2);
            imageView.setImageBitmap(bitmap2);
        }
    }


    public Bitmap getBitmapFromUri(Uri uri, Context context) {
        Bitmap bitmap = null;
        if (uri != null) {
            try {
                // ContentResolver를 통해 URI에서 InputStream을 얻음
                InputStream inputStream = context.getContentResolver().openInputStream(uri);
                // BitmapFactory를 사용하여 InputStream을 비트맵으로 변환
                bitmap = BitmapFactory.decodeStream(inputStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return cropCenterBitmap(bitmap, 224, 224);//중간 300*300픽셀 만큼 자르기
    }

    private Bitmap cropCenterBitmap(Bitmap src, int w, int h) {
        int width = src.getWidth();//비트맵의 너비
        int height = src.getHeight();//비트맵의 높이
        int newX = (width - w) / 2;
        int newY = (height - h) / 2;
        return Bitmap.createBitmap(src, newX, newY, w, h);//새로운 x,y 좌표에서 300,300만큼 자르기
    }

    private MappedByteBuffer loadModelFile(String modelName) throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(modelName);//assets폴더에 model.tflite 열기
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());//fileDescriptor로 불러온 파일을 읽기
        FileChannel fileChannel = inputStream.getChannel();//채널을 통해 접근 : 효율적 접근 가능
        long startOffset = fileDescriptor.getStartOffset();//시작 오프셋 반환
        long declaredLength = fileDescriptor.getDeclaredLength();//선언된 길이 반환
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);//메모리에 직접 읽게함.(읽기전용으로)
    }


    private void classifyImage(Bitmap bitmap) {
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(bitmap);//비트맵을 ByteBuffer로 변환
        StringBuilder results = new StringBuilder();

        float[] maxresult = new float[tflite.length];
        String[] result_class = new String[tflite.length];

        float max = 0;

        for (int modelIdx = 0; modelIdx < tflite.length; modelIdx++) {
            float[][] logits = new float[1][4];
            tflite[modelIdx].run(byteBuffer, logits);

            float[] softmaxProbabilities = applySoftmax(logits[0]);
            int maxIndex = 0;
            float maxProbability = softmaxProbabilities[0];


            for (int i = 1; i < softmaxProbabilities.length; i++) {
                if (softmaxProbabilities[i] > maxProbability) {
                    maxProbability = softmaxProbabilities[i];
                    maxIndex = i;
                }
            }

            /*results.append("모델 ").append(modelIdx + 1).append(": ")
                    .append(classLabels[maxIndex]).append(", 확률: ")
                    .append(String.format("%.2f%%\n", maxProbability * 100));*/

            progressBars[modelIdx].setProgress((int)(maxProbability * 100));

            ResultTxt[modelIdx].setText(classLabels[maxIndex] + ","+(int)(maxProbability * 100) + "%");

            maxresult[modelIdx] = maxProbability;

            result_class[modelIdx] = classLabels[maxIndex];
        }

        for (int i = 1; i < maxresult.length; i++) {
            if (maxresult[i] > max) {
                max = maxresult[i];
                maxIndex = i;
            }
        }

        //resultTextView.setText(results.toString());
        resultTextView.setText("최고 확률 : " + modelNames_KR[maxIndex] + ", "+ (int)(max * 100)  + "%, " + result_class[maxIndex]);

        keyword=modelNames_KR[maxIndex];

        /*Intent intent = new Intent(Image.this, Care.class);
        intent.putExtra("key", keyword);  // "value"는 전달할 데이터입니다.
        startActivity(intent);*/

        // 데이터를 저장합니다.
        SharedPreferences sharedPref = getSharedPreferences("AppName", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("key", keyword);
        editor.apply();

    }

    // 소프트맥스 함수
    private float[] applySoftmax(float[] logits) {
        float maxLogit = Float.NEGATIVE_INFINITY;//마이너스 무한대로 초기화(로짓 배열에서 가장 큰 값 찾기위해서)
        for (float logit : logits) { //최대 logit값 찾기
            if (logit > maxLogit) {
                maxLogit = logit;
            }
        }

        float sum = 0.0f;
        for (int i = 0; i < logits.length; i++) {
            logits[i] = (float) Math.exp(logits[i] - maxLogit);//각 원소에 대해 maxLogit값을 뺀 값을
            sum += logits[i]; // sum에 다 합치기(maxLogit값을 빼는 이유는 수치적 안정성을 높이기 위함)
        }

        for (int i = 0; i < logits.length; i++) {
            logits[i] /= sum;//각 원소에 대해 전체 값으로 나눠서 각 클래스의 확률을 구함
        }
        return logits;//값 반환
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3);//224*224픽셀 이미지에 4바이트와 RGB(3개 채널)를 고려해 계산
        byteBuffer.order(ByteOrder.nativeOrder());////바티트의 순서를 네이티브오더로
        int[] intValues = new int[224 * 224];//
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());//비트맵에서 각 픽셀 값을 추출하여 intValues에 저장후, 모든 픽셀을 한번에 배열로 읽기
        int pixel = 0;
        for (int i = 0; i < 224; ++i) {
            for (int j = 0; j < 224; ++j) {
                int val = intValues[pixel++];
                //채널 순서는 BGR로 구성해놨음. 테스트 후 RGB로 변경해봐야햠
                byteBuffer.putFloat(((val & 0xFF) / 255.0f - 0.406f) / 0.225f);//0.406은 평균, 0.225는 표준편차 => 정규화
                byteBuffer.putFloat((((val >> 8) & 0xFF) / 255.0f - 0.456f) / 0.224f);
                byteBuffer.putFloat((((val >> 16) & 0xFF) / 255.0f - 0.485f) / 0.229f);
            }
        }
        return byteBuffer;
    }
    private void saveBitmapToFirebase(Bitmap bitmap) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String timeStamp = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String fileName = userId + "_" + timeStamp + ".jpg";

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();
        StorageReference imageRef = storageRef.child("images/" + fileName);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] data = baos.toByteArray();

        UploadTask uploadTask = imageRef.putBytes(data);
        uploadTask.addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
            String downloadUrl = uri.toString();
            saveImageMetadataToDatabase(userId, timeStamp, downloadUrl);
        })).addOnFailureListener(exception -> {
            Toast.makeText(Image.this, "업로드 실패: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            imageView.setVisibility(View.GONE);
        });
    }

    private void saveImageMetadataToDatabase(String userId, String timeStamp, String downloadUrl) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference userRef = database.getReference("users").child(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                UserAccount userAccount = dataSnapshot.getValue(UserAccount.class);
                if (userAccount != null) {
                    userAccount.setProfileImageUrl(downloadUrl);
                    userRef.setValue(userAccount).addOnSuccessListener(aVoid -> {
                        Toast.makeText(Image.this, "이미지 업로드 및 사용자 정보 업데이트 성공", Toast.LENGTH_SHORT).show();
                    }).addOnFailureListener(e -> {
                        Toast.makeText(Image.this, "사용자 정보 업데이트 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(Image.this, "사용자 정보 불러오기 실패: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        DatabaseReference imageRef = database.getReference("images").child(userId).child(timeStamp);
        imageRef.setValue(downloadUrl).addOnSuccessListener(aVoid -> {
            Toast.makeText(Image.this, "이미지 업로드 성공", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            Toast.makeText(Image.this, "이미지 메타데이터 저장 실패", Toast.LENGTH_SHORT).show();
        });
    }
}