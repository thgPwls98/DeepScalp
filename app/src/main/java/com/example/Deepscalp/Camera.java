package com.example.Deepscalp;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Camera extends AppCompatActivity {
    private PreviewView viewFinder;
    private ImageCapture imageCapture;
    private Button btnCapture;
    public Bitmap bitmap;
    public Bitmap croppedBitmap;
    private ImageView imageView;

    private TextView resultTextView;

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


    //
    //Camera2 사용 테스트(작동 됨)
    //접사, 확대, 자동초점 등 구현 필요=> 사용 X
    //사용안함
    //사용안함
    //사용안함
    //사용안함
    //사용안함
    //사용안함
    //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        viewFinder = findViewById(R.id.viewFinder);
        btnCapture = findViewById(R.id.btn_capture);
        imageView = findViewById(R.id.imageView);
        resultTextView = findViewById(R.id.resultTextView);


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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { //카메라 권한이 없을 경우
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1001);//카메라 권한 요청
        } else {
            startCamera();//startCamera 메소드 실행
        }

        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (imageCapture != null) { //
                    takePhoto();//takePhoto 메소드 실행
                }
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build(); // 프리뷰 설정 후 빌드

                imageCapture = new ImageCapture.Builder()//ImageCaputre 객체 생성 + 촬영 기능 설정
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)//사진 비율 4:3
                        .setTargetRotation(Surface.ROTATION_0)//회전 0도
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)//촬영 모드 최소 지연
                        .build();

                CameraSelector cameraSelector = null;
                CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                try {
                    for (String cameraId : cameraManager.getCameraIdList()) {
                        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                        Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                        Boolean isMacro = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) == 0;

                        if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK && isMacro) {
                            cameraSelector = new CameraSelector.Builder()
                                    .requireLensFacing(lensFacing)
                                    .build();
                            break;
                        }
                    }
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

                if (cameraSelector == null) {
                    cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build();

                    Toast.makeText(this, "접사 렌즈 없음", Toast.LENGTH_SHORT).show();
                }

                cameraProvider.unbindAll();//이전 카메라 값 모두 해제
                cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);

                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());//뷰파인더에 프리뷰(카메라 화면)를 표시
            } catch (Exception e) {
                Log.e("CameraXApp", "카메라 사용 거부", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /*private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();

                ImageCapture imageCapture = new ImageCapture.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setTargetRotation(Surface.ROTATION_0)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = null;
                CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                try {
                    for (String cameraId : cameraManager.getCameraIdList()) {
                        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                        Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                        Float minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);

                        if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK && minFocusDistance != null && minFocusDistance == 0f) {
                            cameraSelector = new CameraSelector.Builder()
                                    .requireLensFacing(lensFacing)
                                    .build();
                            break; // 매크로 렌즈를 찾았으면 반복 종료
                        }
                    }
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

                if (cameraSelector == null) {
                    cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build();
                    Toast.makeText(this, "접사 렌즈 없음", Toast.LENGTH_SHORT).show();
                }

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);

                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
            } catch (Exception e) {
                Log.e("CameraXApp", "카메라 사용 거부", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }*/


    private void takePhoto() {
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                super.onCaptureSuccess(image);

                bitmap = imageProxyToBitmap(image);//이미지를 비트맵으로 변환

                saveBitmapToGallery(bitmap);//갤러리에 자른 이미지 저장~

                saveBitmapToFirebase(bitmap);


                imageView.setImageBitmap(bitmap);//이미지 뷰에 bitmap으로 표시
                imageView.setVisibility(View.VISIBLE);//이미지뷰는 표시
                viewFinder.setVisibility(View.GONE);//프리뷰는 숨김

                //여기에 이제 딥러닝 모델에 입력하는 코드 추가
                classifyImage(bitmap);

                image.close();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("CameraXApp", "캡쳐 실패: " + exception.getMessage());
            }
        });
    }


    private Bitmap imageProxyToBitmap(ImageProxy image) {//비트맵으로 변환
        ImageProxy.PlaneProxy[] planes = image.getPlanes();//이미지 데이터의 플레인 사용
        ByteBuffer buffer = planes[0].getBuffer();//첫 번째 플레인의 버퍼 가져오기(버퍼에는 이미지의 픽셀 데이터 포함)
        byte[] bytes = new byte[buffer.remaining()];//버퍼의 남은 데이터 크기만큼 바이트 배열 생성(remaining()이 현재 위치에서 남은 바이트 수 반환)
        buffer.get(bytes);//버퍼의 데이터를 바이트 배열에 복사

        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);//바이트 배열을 Bitmap으로 디코딩 후 객체 생성

        // 회전 정보 가져오기
        int rotationDegrees = image.getImageInfo().getRotationDegrees();//이미지의 회전 각도 가져오기
        Bitmap rotatedBitmap = rotateBitmap(bitmap, rotationDegrees);// rotationDegrees를 이용하여 알아서 회전시킴

        // 회전된 이미지 자르기
        return cropCenterBitmap(rotatedBitmap, 224, 224);//중간 300*300픽셀 만큼 자르기
    }


    private Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        Matrix matrix = new Matrix();//Matrix는 2d 변환을 위한 클래스
        matrix.postRotate(rotationDegrees);//rotationDegrees만큼 회전
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);//원본 bitmap을 0,0 부터 시작해서, 전체 너비, 높이까지, matrix 변환, 필터링은 true
    }

    private Bitmap cropCenterBitmap(Bitmap src, int w, int h) {
        int width = src.getWidth();//비트맵의 너비
        int height = src.getHeight();//비트맵의 높이
        int newX = (width - w) / 2;
        int newY = (height - h) / 2;
        return Bitmap.createBitmap(src, newX, newY, w, h);//새로운 x,y 좌표에서 300,300만큼 자르기
    }

    private void saveBitmapToGallery(Bitmap bitmap) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());//이미지의 이름은 촬영된 시간으로 저장하기 위해

        ContentValues contentValues = new ContentValues();//ContentValues는 키-값으로 저장가능한 컨테이너
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timeStamp + ".jpg");//파일 이름은 timestamp + .jpg로 저장
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");//JPEG형식으로 저장
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CameraX-Photos");//이미지가 저장될 경로

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);//contentValues 데이터를 가지고 외부 URI에 저장후 uri 반환

        try {
            if (uri != null) {
                OutputStream outputStream = getContentResolver().openOutputStream(uri);//uri에 대한 출력 스트림
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);//비트맵을 jpeg로 압축 + 100% 품질로 outputStream을 통해 외부저장소에 저장
                    outputStream.close();
                    Toast.makeText(this, "캡쳐 성공: " + uri, Toast.LENGTH_SHORT).show();
                    Log.d("CameraXApp", "캡쳐 성공: " + uri);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        String[] result_class = new String[tflite.length]; // 초기화

        float max = 0; //초기화

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
            Toast.makeText(Camera.this, "업로드 실패: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(Camera.this, "이미지 업로드 및 사용자 정보 업데이트 성공", Toast.LENGTH_SHORT).show();
                    }).addOnFailureListener(e -> {
                        Toast.makeText(Camera.this, "사용자 정보 업데이트 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(Camera.this, "사용자 정보 불러오기 실패: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        DatabaseReference imageRef = database.getReference("images").child(userId).child(timeStamp);
        imageRef.setValue(downloadUrl).addOnSuccessListener(aVoid -> {
            Toast.makeText(Camera.this, "이미지 업로드 성공", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            Toast.makeText(Camera.this, "이미지 메타데이터 저장 실패", Toast.LENGTH_SHORT).show();
        });
    }

}