package com.example.mil3y_test;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
//import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;


import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Character;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;



//import static com.hoho.android.usbserial.util.SerialInputOutputManager.mReadBuffer;

public class TerminalFragment extends Fragment implements SerialInputOutputManager.Listener {

    private enum UsbPermission { Unknown, Requested, Granted, Denied }

    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 10;
    private static final int READ_WAIT_MILLIS = 0;


//    private static final int READ_WAIT_MILLIS = 2000;


    private int deviceId, portNum, baudRate;
    private boolean withIoManager;

    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;
    public static TextView receiveText;
    public static TextView logText;
    //public static ImageView imageView;
    public static FrameLayout frameLayout;
    private ControlLines controlLines;


    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private boolean connected = false;

    //private int globalLen = 3;
    private int totalLen;
    private int bytelen = 0;
    private int input_img_len = 0;

    public byte[] Totaldata = new byte[4];

    public boolean ready = false;

    public List<String> test = new ArrayList<String>();
    public Map<String,String> mapTest = new HashMap();    //<키 자료형, 값 자료형>
    public Map<Integer, List<String> > ExDict = new HashMap();	//<키 자료형, 값 자료형>
    public Map<Integer, String>  FeedD = new HashMap();	//<키 자료형, 값 자료형>

    public String ExNum;

    public int [][] locarray ;
    int phase_amount = 0;
    String[] Category;

//    private byte[] Final = new byte[4];


    public TerminalFragment() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                    connect();
                }
            }
        };
        mainLooper = new Handler(Looper.getMainLooper());

    }

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
        withIoManager = getArguments().getBoolean("withIoManager");
        makeExDict();
        makefeedD();
    }

    private void makeExDict(){

        ExDict.put(1, Arrays.asList("목 옆으로 당기기" , "0", "3")); //운동명, 운동방향(1:측면), 전체 phase수
        ExDict.put(2, Arrays.asList("목 앞으로 당기기" , "1", "2" ));
        ExDict.put(3, Arrays.asList("깍지끼고 가슴펴기" , "1", "1" ));
        ExDict.put(4, Arrays.asList("어깨 십자 당기기" , "0", "2" ));


    }

    private void makefeedD(){
        FeedD.put(1, "정면보세요");
        FeedD.put(0, "측면보세요");


    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));

        if(usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
            mainLooper.post(this::connect);
    }

    @Override
    public void onPause() {
        if(connected) {
            status("disconnected");
            disconnect();
        }
        getActivity().unregisterReceiver(broadcastReceiver);
        super.onPause();
    }


    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        logText = view.findViewById(R.id.log);                          // TextView performance decreases with number of spans
        logText.setMovementMethod(ScrollingMovementMethod.getInstance());
//        logText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans


        //imageView = view.findViewById(R.id.imageView);
        frameLayout = view.findViewById(R.id.frameLayout);


        TextView sendText = view.findViewById(R.id.send_text);
        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
        View receiveBtn = view.findViewById(R.id.receive_btn);
        controlLines = new ControlLines(view);
        if(withIoManager) {
            receiveBtn.setVisibility(View.GONE);
        } else {
            receiveBtn.setOnClickListener(v -> read());
        }
        return view;




    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if( id == R.id.send_break) {
            if(!connected) {
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    usbSerialPort.setBreak(true);
                    Thread.sleep(100); // should show progress bar instead of blocking UI thread
                    usbSerialPort.setBreak(false);
                    SpannableStringBuilder spn = new SpannableStringBuilder();
                    spn.append("send <break>\n");
                    spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    receiveText.append(spn);
                } catch(UnsupportedOperationException ignored) {
                    Toast.makeText(getActivity(), "BREAK not supported", Toast.LENGTH_SHORT).show();
                } catch(Exception e) {
                    Toast.makeText(getActivity(), "BREAK failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial
     */

    @Override
    public void onRunError(Exception e) {
        mainLooper.post(() -> {
            status("connection lost: " + e.getMessage());
            disconnect();
        });
    }

    /*
     * Serial + UI
     */
    private void connect() {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for(UsbDevice v : usbManager.getDeviceList().values())
            if(v.getDeviceId() == deviceId)
                device = v;
        if(device == null) {
            status("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if(driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if(driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
            if(withIoManager) {
                usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
                usbIoManager.start();
            }
            status("connected");
            status("운동을 선택해주세요");
            
            connected = true;
            controlLines.start();
        } catch (Exception e) {
            status("connection failed: " + e.getMessage());
            disconnect();
        }
    }

    private void disconnect() {
        connected = false;
        controlLines.stop();
        if(usbIoManager != null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
        }
        usbIoManager = null;
        try {
            usbSerialPort.close();
        } catch (IOException ignored) {}
        usbSerialPort = null;
    }

    // 안드로이드에서 라즈베리로 데이터 전송은 str 형태로 보냄
    private void send(String str) {
        if(!connected) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // 안드로이드에서 보낸 운동번호가 ExDict의 키값
            ExNum = str;

            //보낸 str형태를 data라는 변수에 byte array로 형변환
            byte[] data = (str + '\n').getBytes();

            phase_amount = Integer.parseInt(ExDict.get(Integer.parseInt(ExNum)).get(2));

            SpannableStringBuilder spn = new SpannableStringBuilder();
            spn.append("운동명 : "+ ExDict.get(Integer.parseInt(ExNum)).get(0)+"\n");
            spn.append("운동 방향 : "+ printState(ExDict.get(Integer.parseInt(ExNum)).get(1))+"\n");
            spn.append("phase 수 : "+ phase_amount+"\n");

            // Integer.parseInt(ExDict.get(ExNum).get(2))
            //spn.append("send " + data.length + " bytes\n");
            //spn.append(HexDump.dumpHexString(data)).append("\n");


            //인덱스에 맞는 이미지 띄움
            //동작 바뀌면 초기화
            if (frameLayout.getChildCount()>1){
                frameLayout.removeAllViews();
                logText.setText("");
                //frameLayout.addView(imageView);
            }
            //showimage(str);
            showimage2(str);
            drawborder(0); //처음 시작할 때 0번 째 그림 테두리

            readDataFromCsv("location.csv",str);
            spn.append("좌표: "+Arrays.deepToString(locarray)+"\n");
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            receiveText.append(spn);
            usbSerialPort.write(data, WRITE_WAIT_MILLIS);

        } catch (Exception e) {
            onRunError(e);
        }
    }

    private String printState(String str) {

        String state = null;

        if(str.equals("0")){
            state = "정면";
        }
        if(str.equals("1")){
            state = "측면";
        }

        return state;
    }

    //@Override
//    public void onNewData2(byte[] data) { //이미지
//       if (ready==false){
//           mainLooper.post(() -> {
//               concatTest_f(data); //이미지길이 받아옴
//           });
//       }
//
//       else{
//           mainLooper.post(() -> {
//
//               concatTest_t(data); //이미지 array 받아옴
//           });
//       }
//
//        //logText.setText(Arrays.toString(data));
//        //TerminalFragment.receiveText.setText("onNewData func");
//    }
    boolean ready2 = false;

    @Override
    public void onNewData(byte[] data) { //json

        if (ready2 == false){
            mainLooper.post(() -> {
                json_len(data); //json길이 받아옴

            });
        }
        else{
            mainLooper.post(() -> {

                receiveOften(data); //json처리

            });
        }

    }


    public void readDataFromCsv(String filePath, String index) throws IOException {

        String[] nextLine;
        List<String> messages = null;

        AssetManager am = getResources().getAssets() ;
        try {
            int p = 0;
            //int[][] arr;
            InputStreamReader is = new InputStreamReader(getResources().getAssets().open(filePath));
            CSVReader reader = new CSVReader(is);// 1
            reader.skip(1);
            //BufferedReader reader = new BufferedReader(is);
            //reader.readLine();


            while (p!=Integer.parseInt(index)){ //(reader.readNext() != null){
                nextLine = reader.readNext(); // [2,목 앞으로 당기기,"[]","[]"]
                messages = Arrays.asList(nextLine[2].split(","));
                String[] num = nextLine[0].split(",");
                Category = nextLine[3].split(",");
                p = p + 1;

                if (Objects.equals(num[0], index)){//Integer.parseInt(index))
                    break;
                }
                    //System.out.print(Integer.parseInt(messages.get(0)));
                    //System.out.print(Arrays.deepToString(locarray))

                }

            locarray = new int[messages.size()/2][2];
            for (int j = 0; j < messages.size(); j++) {

                if (j % 2 == 0) {
                    locarray[j / 2][0] = Integer.parseInt(messages.get(j));
                }
                else {
                    locarray[j / 2][1] = Integer.parseInt(messages.get(j));}
                }
            //logText.setText("done"+messages+messages.size());

        }catch (IOException | CsvValidationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();}
    }



    private void drawborder(int num){
        TableLayout kk =  frameLayout.findViewById(tableid);
        String tag = "iv_"+num;
        ImageView aa = kk.findViewWithTag(tag);

        // 새로 그림
        if(num>=0 && num != phase_amount){
            aa.setBackgroundResource(R.drawable.border);

            if (num-1>=0){ // 이전꺼 지움
                ImageView erase = kk.findViewWithTag("iv_"+(num-1));
                erase.setPadding(0,0,0,0);
                erase.setBackgroundResource(0);
            }

        }

        else if (num == phase_amount) { // 마지막꺼 지움
            ImageView erase = kk.findViewWithTag("iv_" + (num - 1));
            erase.setPadding(0, 0, 0, 0);
            erase.setBackgroundResource(0);
        }

        //logText.setText("bottom"+aa.getBottom()+"right"+aa.getLeft()+"num"+num);

    }




    int tableid;
    private void showimage2(String index) throws IOException {
        /// 400*405
        int img_h = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 400, getResources().getDisplayMetrics());
        int img_w = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 405, getResources().getDisplayMetrics());

        int cc = 0;
        //LinearLayout.LayoutParams imparam = new LinearLayout.LayoutParams(img_w /* layout_width */,img_h /* layout_height */, 1f /* layout_weight */);

        TableLayout table = new TableLayout(getContext());

        table.setId(View.generateViewId());
        tableid= table.getId();
        //logText.setText("id"+String.valueOf(tableid));
        table.setLayoutParams(new LinearLayout.LayoutParams(img_w, img_h)); // framelayout이랑 같은 사이즈로 가져옴



        int phase = phase_amount;
        int len = phase/4;
        if (len<1){
            len = 1;
        }else if (phase==6){
            len = phase/3; // 이미지 8개, 6개면 2줄
        }

        TableRow[] rows = new TableRow[len];

        for (int rr = 0; rr<= rows.length-1; ++rr) {
            //TableRow[] rows = new TableRow[3]; // 행 설정

            rows[rr]   = new TableRow(getContext());
            //TableRow rows = new TableRow(getContext());
            rows[rr].setLayoutParams(new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, img_w, 0.25f));

            for (cc = 0; cc <= (phase/len)-1; ++cc) { // (페이즈 수 / 열 수)

                String filename = "move_" + index + "_" +(cc+phase/len*rr)+ ".jpg";
                AssetManager am = getResources().getAssets();
                InputStream is = null;

                ImageView iv = new ImageView(getContext());  // 운동 이미지 imageView 생성
                iv.setScaleType(ImageView.ScaleType.FIT_XY);
                iv.setTag("iv_"+(cc+phase/len*rr));
                //iv.setBackgroundResource(R.drawable.border);

                try {
                    is = am.open(filename);
                    Bitmap bm = BitmapFactory.decodeStream(is);
                    BitmapDrawable ob = new BitmapDrawable(getResources(), bm);

                    iv.setImageBitmap(bm);

                    TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.MATCH_PARENT);
                    layoutParams.weight = 1;

                    iv.setLayoutParams(layoutParams);

                    rows[rr].addView(iv);
                    is.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            table.addView(rows[rr]);



        }

        frameLayout.addView(table);
        draw_arrow();



    }


    int json_len;
    private void json_len(byte[] data){ //json 길이 받아옴
        int len = data.length;  //개별 bytearray data

        if (bytelen != 4) {
            System.arraycopy(Arrays.copyOf(data, len), 0, Totaldata, bytelen, Arrays.copyOf(data, len).length);
        }
        bytelen = bytelen + len;
        if (bytelen == 4) {
            int arr_len;
            byte[] dataBuffer = new byte[4];

            dataBuffer[0] = Totaldata[3];
            dataBuffer[1] = Totaldata[2];
            dataBuffer[2] = Totaldata[1];
            dataBuffer[3] = Totaldata[0];

            bytelen = 0;

            arr_len = byteToint(dataBuffer);
            json_len = arr_len;

            //logText.setText("json array length: "+String.valueOf(json_len));
            ready2 = true;


        }
    }



    int datalen;
    //List<String> j_list= new ArrayList<>(json_len);
    //String j_list2="";
    String j_list3 ="";
    byte[] br;

    private void receiveOften(byte[] data) {
        //j_list = {"score":19,"direction":0} 형태로 받을 때
        //j_list2 = [13,36,71,22] 형태로 받을 때
        //j_list3 = {"score":[12,26,72],"direction":0} 형태로 받을 때

        if (datalen != json_len){
            for (byte i : data){
                //j_list.add( Character.toString( (char) i));
                //j_list2 = j_list2+Character.toString( (char) i);
                j_list3 =  j_list3+Character.toString( (char) i);
            }
            //System.arraycopy(Arrays.copyOf(data, data.length), 0, j_list, datalen, Arrays.copyOf(data,  data.length).length);
            logText.setText("!=");
        }

        datalen = datalen + data.length;

        if (datalen == json_len){

            try {
                //점수 리스트
                JSONObject jsonObject = new JSONObject(j_list3);
                List<String> scores=  new ArrayList<String>();
                int phase = -1;

                if (jsonObject.has("avgscore")){
                    JSONArray score_ja = jsonObject.getJSONArray("avgscore");
                    for(int i=0; i < score_ja.length(); i++) {
                        scores.add(score_ja.getString(i));
                    }


                    drawborder(phase_amount); //마지막 종합점수 보여줄 때 마지막 꺼 지움
                    logText.setText("avgscore:"+scores);

                }
                else{
                    JSONArray score_ja = jsonObject.getJSONArray("score");
                    for(int i=0; i < score_ja.length(); i++) {
                        scores.add(score_ja.getString(i));
                    }

                    //정면or측면 판단
                    String feedback = jsonObject.getString("direction");
                    String direc = FeedD.get(Integer.parseInt(feedback)) ;

                    //reps
                    phase = 0;
                    if (jsonObject.has("reps")){
                        phase = jsonObject.getInt("reps");
                    }

                    logText.setText("direction:"+direc+"  phase:"+Integer.valueOf(phase));

                }

                create_textview(scores,phase);

                if (Category.equals("4")){
                    if (phase != -1 && phase+1 != phase_amount){
                        drawborder(phase+1); // 점수 리스트 받고 다음 페이즈 강조
                    }
                    else if (phase+1 == phase_amount){ //마지막 점수 오면 마지막꺼 지우고 0번재로 그림
                        drawborder(phase_amount);
                        drawborder(0);
                    }

                }else{
                    drawborder(phase_amount);
                    drawborder(phase);

                }


            } catch (JSONException e) {
                e.printStackTrace();
            }

        }


    }




    private void draw_arrow(){

        final int width = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 405, getResources().getDisplayMetrics());
        final int height = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 400, getResources().getDisplayMetrics());

        int x,y,z,interval;

        if (phase_amount<6 || phase_amount==7){ // 한줄에 이미지가 다 보이는 형태
            y = height / 2;
            x = width / (phase_amount);
            z =1;
            interval = phase_amount-1;

        }else if(phase_amount==1){
            return;
        }

       else{ // 두줄에 이미지가 다 보이는 형태
            x = width/(phase_amount/2);
            y = height / 4;
            z= 2;
            interval = phase_amount /2 ;
       }

        for (int j = 0; j<z; j++){
            for (int i =0; i<interval; i++){
                FrameLayout.LayoutParams ar_param = new FrameLayout.LayoutParams(width,height);
                ImageView arr_iv = new ImageView(getContext());

                ar_param.width = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100, getResources().getDisplayMetrics());
                ar_param.height = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100, getResources().getDisplayMetrics());

                ar_param.leftMargin=x- ar_param.width/2;// 화살표가 imageview 중앙에 오기때문에 가로 세로 길이의 절반씩 빼줘야 중앙에 배치됌
                ar_param.topMargin =y- ar_param.height /2;// 


                arr_iv.setImageResource(R.drawable.ic_baseline_arrow_right_24);
                arr_iv.setLayoutParams(ar_param);
                frameLayout.addView(arr_iv);

                x+=x;
            }
            y=y*3;
        }

    }

    private void create_textview(List<String> scorelist,int phase){

        final int width = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 405, getResources().getDisplayMetrics());
        final int height = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 400, getResources().getDisplayMetrics());

        List<String> s1 = scorelist;
        int score_amount = locarray.length/phase_amount;// 랩스당 표시할 점수 갯수

        int[][] arr = locarray; // [[a,b],[c,d]] 형태


        int from = score_amount*phase;
        int to = from + scorelist.size();

        //logText.setText("f"+from+"t"+to);


        if (phase == -1){ //전체 점수 출력
            from = 0;
            to = s1.size();
        }

        //int[][] arr = {{150,100}, {150,210}};
        //logText.setText(Arrays.toString(s1));
        int ss=0;
        for (int i =from; i <to; i++){
            //logText.setText(i+"coord:"+arr[i][0]+arr[i][1]);
            FrameLayout.LayoutParams param = new FrameLayout.LayoutParams(width,height);
            TextView textViewNm = new TextView(getContext());

            param.width = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics());
            param.height = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics());
            param.leftMargin = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, arr[i][0], getResources().getDisplayMetrics());
            param.topMargin = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, arr[i][1], getResources().getDisplayMetrics());

            String sco = s1.get(ss);
            //int sco = s1[i];

            textViewNm.setText(String.valueOf(sco));
            textViewNm.setTypeface(null, Typeface.BOLD);

            //textViewNm.layout( arr[i][0], arr[i][1],0,0);


            if (Float.parseFloat(sco) >= 80){
                textViewNm.setBackgroundColor(Color.rgb(249,215,28));
                if (Float.parseFloat(sco) >= 90){
                    textViewNm.setBackgroundColor(Color.rgb(0,0,255));
                }
            }
            else{
                textViewNm.setBackgroundColor(Color.rgb(255,0,0));
            }

            textViewNm.setLayoutParams(param);
            frameLayout.addView(textViewNm);
            ss +=1;

            //다음 값을 받기위한 초기화
            ready2 = false;
            datalen = 0;
            json_len = 0;
            j_list3 = "";

        }
    }

//////////////////////////////////////////////////////////////////////////////////////////////////////
private void showimage(String index){


//        int dimensionInDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, getResources().getDisplayMetrics());
//        imageView.getLayoutParams().height = dimensionInDp;
//        imageView.getLayoutParams().width = dimensionInDp;
//        imageView.requestLayout();



    String filename = "move_"+index+".jpg";
    AssetManager am = getResources().getAssets() ;
    InputStream is = null ;

    try {
        // 애셋 폴더에 저장된 field.png 열기.
        //is = am.open("field.png") ;
        is = am.open(filename) ;

        // 입력스트림 is를 통해 field.png 을 Bitmap 객체로 변환.
        Bitmap bm = BitmapFactory.decodeStream(is) ;

        // 만들어진 Bitmap 객체를 이미지뷰에 표시.

        //ImageView imageView = (ImageView) findViewById(R.id.image1) ;
        //imageView.setImageBitmap(bm) ;

        is.close() ;
    } catch (Exception e) {
        e.printStackTrace();
    }

    if (is != null) {
        try {
            is.close() ;
        } catch (Exception e) {
            e.printStackTrace() ;
        }
    }

}



    private void receiveStateTest(List<String> data) {

        // float receive
        SpannableStringBuilder spn = new SpannableStringBuilder();

        //자세 형태 출력
        if(data.size() > 0)
            spn.append(String.valueOf(mapTest)).append("\n");
        receiveText.append(spn);

    }



    private void findIndex(List<String> list){
    int divideI = list.indexOf(",");
    StringBuilder scoreSb = new StringBuilder();

    List<String> firstList = list.subList(0, divideI);
    List<String> secondList = list.subList(divideI, list.size());


    int firstI = firstList.indexOf(":");
    int secondI = secondList.indexOf(":");
    String feedback = new String ();
    //feedback = feedback.concat(secondList.get(secondI+ 2)); //.concat(secondList.get(secondI+ 3));

    for (int i = secondI + 2; i < secondList.size()-1; i++) {
        feedback = feedback.concat(secondList.get(i));
    }

    for (String ch : firstList.subList(firstI + 1, firstList.size())){
        scoreSb.append(ch.charAt(0));
    }

    //print_often(scoreSb, feedback);

    //log check
    //logText.setText(" firstI " + firstI+" secondI " + secondI+secondList.size());
    //logText.setText( scoreSb + feedback);


}


    private void print_often(StringBuilder score, String feedback){

        int fnum = Integer.parseInt(feedback);
        logText.setText("점수:"+ score+"\t\t\t\t피드백:"+ FeedD.get(fnum));

        //다음 값을 받기위한 초기화
        ready2 = false;
        datalen = 0;
        json_len = 0;
        //j_list2="";
        j_list3 = "";
    }

    public int total_imgLen;
    public byte[] img_list;

    public int byteToint(byte[] arr){
        return (arr[0] & 0xff)<<24 | (arr[1] & 0xff)<<16 |
                (arr[2] & 0xff)<<8 | (arr[3] & 0xff);
    }

    private void concatTest_t(byte[] data){ // 이미지 byte array 쌓아둠
        int len = data.length;

//        if (String.valueOf(total_imgLen).equals(String.valueOf(input_img_len))) {
//        //if(total_imgLen==input_img_len){
//            //logText.setText("Done total:" +img_list.length);
//            receiveTest2(img_list);
//        }
//        else{
//            System.arraycopy(Arrays.copyOf(data, len), 0, img_list, input_img_len, Arrays.copyOf(data, len).length);
//            logText.setText("loop: "+i+" Remain: "+String.valueOf(total_imgLen)+"/"+input_img_len);
//            input_img_len = input_img_len +  len;
//            i = i+1;
//        }

        if (!String.valueOf(total_imgLen).equals(String.valueOf(input_img_len))) {
            System.arraycopy(Arrays.copyOf(data, len), 0, img_list, input_img_len, Arrays.copyOf(data, len).length);
            //logText.setText("loop: "+i+" Remain: "+String.valueOf(total_imgLen)+"/"+input_img_len);
            input_img_len = input_img_len +  len;
            //i = i+1;

            if(String.valueOf(total_imgLen).equals(String.valueOf(input_img_len))){

                array2bitmap(img_list);
            }
        }

    }

    private void array2bitmap(byte[] data){ // 이미지 화면에 뿌려줌

        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
        //logText.setText("1");

        //imageView.setImageBitmap(bmp);
        
        //다음 이미지를 위한 초기화
        total_imgLen = 0;
        input_img_len=0;
        ready = false;
        //i = 0;

    }

    private void concatTest_f(byte[] data){ // 이미지 array 길이 받아옴
        int len = data.length;  //개별 bytearray data


        if (bytelen != 4) {
            System.arraycopy(Arrays.copyOf(data, len), 0, Totaldata, bytelen, Arrays.copyOf(data, len).length);
        }
        bytelen = bytelen + len;
        if (bytelen == 4) {
            int img_len;
            byte[] dataBuffer = new byte[4];

            dataBuffer[0] = Totaldata[3];
            dataBuffer[1] = Totaldata[2];
            dataBuffer[2] = Totaldata[1];
            dataBuffer[3] = Totaldata[0];

            bytelen = 0;

            img_len = byteToint(dataBuffer);

            total_imgLen = img_len;
            logText.setText("img array length: "+String.valueOf(img_len));


//                int a = byteToint(dataBuffer);
//                int b = 71382;
//
//                if (String.valueOf(a).equals(String.valueOf(b))){
//                    logText.setText("same2");
//                }
            img_list = new byte[total_imgLen];
            ready = true;


        }
    }
    private void concat(byte[] data){ // 실수 한개 받아옴

        int len = data.length;  //개별 bytearray data

        if (totalLen != 4){
            System.arraycopy(Arrays.copyOf(data, len), 0, Totaldata, totalLen, Arrays.copyOf(data, len).length);

        }

        totalLen = totalLen + len;
        if (totalLen == 4){

            byte[] dataBuffer = new byte[4];

            dataBuffer[0] = Totaldata[3];
            dataBuffer[1] = Totaldata[2];
            dataBuffer[2] = Totaldata[1];
            dataBuffer[3] = Totaldata[0];



            ByteBuffer buf = ByteBuffer.wrap(dataBuffer);
//            testSendFloat(dataBuffer[0] << 24 | (dataBuffer[1] & 0xFF) << 16 | (dataBuffer[2] & 0xFF) << 8 | (dataBuffer[3] & 0xFF));
            receive(dataBuffer);

            totalLen = 0;

        }
    }

    private void concatTest3(byte[] data){ // 운동마다 각도 받아옴 ExDic 사용 ('스쿼트','2')
        // ExDict.get(1).get(0)
        int globalLen = Integer.parseInt(ExDict.get(Integer.parseInt(ExNum)).get(2)); //전체 arraylist 요소 개수
        logText.setText(ExNum);

        int len = data.length;  //개별 bytearray data

        if (totalLen != 4){
            System.arraycopy(Arrays.copyOf(data, len), 0, Totaldata, totalLen, Arrays.copyOf(data, len).length);

        }

        totalLen = totalLen + len;

        //logText.setText(Integer.toString(totalLen));


        if (totalLen == 4){

            byte[] dataBuffer = new byte[4];

            dataBuffer[0] = Totaldata[3];
            dataBuffer[1] = Totaldata[2];
            dataBuffer[2] = Totaldata[1];
            dataBuffer[3] = Totaldata[0];



            totalLen = 0;

            //test.add(bytearray2float(dataBuffer));

            //logText.setText("");

//            logText.setText(bytearray2float(dataBuffer) + "  ");
            //logText.setText(Integer.toString(test.size()));

            if(test.size() == globalLen){

                receiveTest(test);
                test.clear();
            }

        }

    }

    private void receiveTest(List<String> data) {

        // float receive
        SpannableStringBuilder spn = new SpannableStringBuilder();

        //hash map + float list 형태 출력
        //mapTest.put("target_move", "squat");
        //mapTest.put("angles", String.valueOf(data));



        if(data.size() > 0)
            //spn.append(String.valueOf(mapTest)).append("\n");
            //(ExDict.get(Integer.parseInt(ExNum)).get(0))
            spn.append("운동 각도 : " + String.valueOf(data)).append("\n");
        receiveText.append(spn);

    }

    private void concatTest(byte[] data){ // 이미지 테스트

        int len = data.length;  //개별 bytearray data

//        if (ready==false) {
//            if (bytelen != 4) {
//                System.arraycopy(Arrays.copyOf(data, len), 0, Totaldata, bytelen, Arrays.copyOf(data, len).length);
//            }
//            bytelen = bytelen + len;
//            if (bytelen == 4) {
//                int img_len;
//                byte[] dataBuffer = new byte[4];
//
//                dataBuffer[0] = Totaldata[3];
//                dataBuffer[1] = Totaldata[2];
//                dataBuffer[2] = Totaldata[1];
//                dataBuffer[3] = Totaldata[0];
//
//                bytelen = 0;
//
//                img_len = byteToint(dataBuffer);
//
//                total_imgLen = img_len;
//                //logText.setText(String.valueOf(img_len));
//                ready = true;
//
////                int a = byteToint(dataBuffer);
////                int b = 71382;
////
////                if (String.valueOf(a).equals(String.valueOf(b))){
////                    logText.setText("same2");
////                }
//                img_list = new byte[total_imgLen];
//
//            }
//        }

//        int total_imgLen = 71382;
//        if (ready==true){
//            //if (String.valueOf(total_imgLen).equals(String.valueOf(input_img_len))) {
//            if(total_imgLen==input_img_len){
//                logText.setText("Done total:" + String.valueOf(total_imgLen));
//                receiveTest2(img_list);
//            }
//            else{
//                System.arraycopy(Arrays.copyOf(data, len), 0, img_list, input_img_len, Arrays.copyOf(data, len).length);
//                logText.setText("loop: "+i+"img_list_len: "+input_img_len);
//                input_img_len = input_img_len + len;
//                i = i+1;
//            }
//        }




        int globalLen = 71382;

        if (input_img_len<globalLen){
            System.arraycopy(Arrays.copyOf(data, len), 0, img_list, input_img_len, Arrays.copyOf(data, len).length);
            //logText.setText("loop: "+i+"img_list_len:"+input_img_len);
            input_img_len = input_img_len + len;
            //i = i+1;

            //log check
            List<Byte> list = new ArrayList<>();
            for (byte i : data){
                list.add((i));
            }
        }
        if (input_img_len == globalLen) {
            logText.setText("Done" + "input_img_len" + input_img_len);
            array2bitmap(img_list);
        }
        if (input_img_len > globalLen) {
            logText.setText("Larger than" + "input_img_len" + input_img_len);
        }


    }

    private float bytearray2float(byte[] data){

        // 정수형(int)으로 변환
        int iTemp = (data[0] << 24) & 0xff000000;
        iTemp |= (data[1] << 16) & 0x00ff0000;
        iTemp |= (data[2] << 8) & 0x0000ff00;
        iTemp |= (data[3]) & 0x000000ff;

//        Log.d("test", String.valueOf(Float.intBitsToFloat(iTemp)));

        //return String.valueOf(Float.intBitsToFloat(iTemp));
        return Float.intBitsToFloat(iTemp);

    }

    private void testSendFloat(float data) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        spn.append((char) data);
        receiveText.append(spn);

    }

    private void receive(byte[] data) { //float로 변환 후 띄워줌

        // float receive
        SpannableStringBuilder spn = new SpannableStringBuilder();

        // 정수형(int)으로 변환
        int iTemp = (data[0] << 24) & 0xff000000;
        iTemp |= (data[1] << 16) & 0x00ff0000;
        iTemp |= (data[2] << 8) & 0x0000ff00;
        iTemp |= (data[3]) & 0x000000ff;


        if(data.length > 0)
            spn.append(String.valueOf(Float.intBitsToFloat(iTemp))).append("\n");
        receiveText.append(spn);

    }

    private void receiveDictTest(List<String> data) {

        // float receive
        SpannableStringBuilder spn = new SpannableStringBuilder();

        mapTest.put("target_move", "squat");
        mapTest.put("angles", String.valueOf(data));

        if(data.size() > 0)
            spn.append(String.valueOf(mapTest)).append("\n");
        receiveText.append(spn);

    }


    //////////////////////////////////////////////////////////////////

    /*
     * 이 아래는 수정금지!
     */

    private void read() {
        if(!connected) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {


            byte[] buffer = new byte[8192];
            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);

            receive(Arrays.copyOf(buffer, len));
//
//            byte[] test = {0x48, 0x65, (byte)0x6C, (byte)0x6C, (byte)0x6f,
//                    0x20, 0x57, (byte)0x6f, 0x72, (byte)0x6c, 0x64};
//            receive(test);
//            byte[] dataFinal = new byte[8192];  //data 배열에 전체 byte 담아야 함




//            byte[] buffer = new byte[8];
//            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);
//
//            byte[] dataFinal = new byte[8192];  //data 배열에 전체 byte 담아야 함
//
//            for (int i = 0; i<8; i++){
//
//                System.arraycopy(Arrays.copyOf(buffer, len), 0, dataFinal, i, buffer.length);
//                receive(dataFinal);
//                finalLength = i;
//
//            }

//            receive(dataFinal);

//            byte[] buffer = new byte[8192];
//            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);
//            receive(Arrays.copyOf(buffer, len));
//            receive(Arrays.copyOf(buffer, len));


        } catch (IOException e) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            status("connection lost: " + e.getMessage());
            disconnect();
        }
    }

    void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }
    class ControlLines {

        private static final int refreshInterval = 200; // msec 200

        private final Runnable runnable;
        //private final ToggleButton rtsBtn, ctsBtn, dtrBtn, dsrBtn, cdBtn, riBtn;

        ControlLines(View view) {
            runnable = this::run; // w/o explicit Runnable, a new lambda would be created on each postDelayed, which would not be found again by removeCallbacks

//            rtsBtn = view.findViewById(R.id.controlLineRts);
//            ctsBtn = view.findViewById(R.id.controlLineCts);
//            dtrBtn = view.findViewById(R.id.controlLineDtr);
//            dsrBtn = view.findViewById(R.id.controlLineDsr);
//            cdBtn = view.findViewById(R.id.controlLineCd);
//            riBtn = view.findViewById(R.id.controlLineRi);
//            rtsBtn.setOnClickListener(this::toggle);
//            dtrBtn.setOnClickListener(this::toggle);
        }

        private void toggle(View v) {
            ToggleButton btn = (ToggleButton) v;
            if (!connected) {
                btn.setChecked(!btn.isChecked());
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
                return;
            }
            String ctrl = "";
//            try {
//                if (btn.equals(rtsBtn)) { ctrl = "RTS"; usbSerialPort.setRTS(btn.isChecked()); }
//                if (btn.equals(dtrBtn)) { ctrl = "DTR"; usbSerialPort.setDTR(btn.isChecked()); }
//            } catch (IOException e) {
//                status("set" + ctrl + "() failed: " + e.getMessage());
//            }
        }

        private void run() {
            if (!connected)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getControlLines();
//                rtsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RTS));
//                ctsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CTS));
//                dtrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DTR));
//                dsrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DSR));
//                cdBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CD));
//                riBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RI));
                mainLooper.postDelayed(runnable, refreshInterval);
            } catch (IOException e) {
                status("getControlLines() failed: " + e.getMessage() + " -> stopped control line refresh");
            }
        }

        void start() {
            if (!connected)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getSupportedControlLines();
//                if (!controlLines.contains(UsbSerialPort.ControlLine.RTS)) rtsBtn.setVisibility(View.INVISIBLE);
//                if (!controlLines.contains(UsbSerialPort.ControlLine.CTS)) ctsBtn.setVisibility(View.INVISIBLE);
//                if (!controlLines.contains(UsbSerialPort.ControlLine.DTR)) dtrBtn.setVisibility(View.INVISIBLE);
//                if (!controlLines.contains(UsbSerialPort.ControlLine.DSR)) dsrBtn.setVisibility(View.INVISIBLE);
//                if (!controlLines.contains(UsbSerialPort.ControlLine.CD))   cdBtn.setVisibility(View.INVISIBLE);
//                if (!controlLines.contains(UsbSerialPort.ControlLine.RI))   riBtn.setVisibility(View.INVISIBLE);
                run();
            } catch (IOException e) {
                Toast.makeText(getActivity(), "getSupportedControlLines() failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        void stop() {
            mainLooper.removeCallbacks(runnable);
//            rtsBtn.setChecked(false);
//            ctsBtn.setChecked(false);
//            dtrBtn.setChecked(false);
//            dsrBtn.setChecked(false);
//            cdBtn.setChecked(false);
//            riBtn.setChecked(false);
        }
    }
}
