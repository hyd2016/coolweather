package com.example.coolweather;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.coolweather.db.CIty;
import com.example.coolweather.db.County;
import com.example.coolweather.db.Province;
import com.example.coolweather.util.HttpUtil;
import com.example.coolweather.util.Utility;

import org.jetbrains.annotations.NotNull;
import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {
    private static final String TAG = "ChooseAreaFragment";
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;
    private ProgressDialog progressDialog;
    private TextView titleText;
    private Button backButton;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> dataLIst = new ArrayList<>();
    /**
     * 省列表
     */
    private List<Province> provinceList;
    /**
     * 市列表
     */
    private List<CIty> cItyList;
    /**
     * 县列表
     */
    private List<County> countyList;

    /**
     * 选中省份
     */
    private Province selectedProvince;
    private CIty selectedCity;
    private County selectedCounty;
    /**
     * 当前选中级别
     */
    private int currentLevel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area,container,false);
        titleText  = view.findViewById(R.id.title_text);
        backButton = view.findViewById(R.id.back_button);
        listView = view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(Objects.requireNonNull(getContext()),android.R.layout.simple_list_item_1,dataLIst);
        listView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (currentLevel == LEVEL_PROVINCE){
                selectedProvince = provinceList.get(position);
                queryCities();
            }else if (currentLevel == LEVEL_CITY){
                selectedCity = cItyList.get(position);
                queryCounties();
            }else if (currentLevel == LEVEL_COUNTY){
                String weatherId = countyList.get(position).getWeatherId();
                if (getActivity() instanceof MainActivity){
                    Intent intent = new Intent(getActivity(),WeatherActivity.class);
                    intent.putExtra("weather_id",weatherId);
                    startActivity(intent);
                    Objects.requireNonNull(getActivity()).finish();
                }else if (getActivity() instanceof WeatherActivity){
                    WeatherActivity activity = (WeatherActivity) getActivity();
                    activity.drawerLayout.closeDrawers();
                    activity.swipeRefreshLayout.setRefreshing(true);

                    activity.requestWeather(weatherId);
                }

            }
        });
        backButton.setOnClickListener(v -> {
            if (currentLevel == LEVEL_COUNTY){
                queryCities();
            }else if (currentLevel == LEVEL_CITY){
                queryProvinces();
            }
        });
        queryProvinces();
    }

    /**
     * 查询全国所有的省，优先从数据库查询，若果没有再到服务器查询
     */

    private void queryProvinces() {
        titleText.setText("中国");
        backButton.setVisibility(View.GONE);
        provinceList = DataSupport.findAll(Province.class);
        if (provinceList.size()>0){
            dataLIst.clear();
            for (Province province:provinceList){
                dataLIst.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;

        }else {
            String address = "http://guolin.tech/api/china";
            queryFromServer(address,"province");
        }
    }

    /**
     * 查询全国所有的市，优先从数据库查询，若果没有再到服务器查询
     */
    private void queryCities() {
        titleText.setText(selectedProvince.getProvinceName());
        backButton.setVisibility(View.VISIBLE);
        cItyList = DataSupport.where("provinceid = ?",String .valueOf(selectedProvince.getId())).find(CIty.class);
        Log.d(TAG, "queryCities: " + cItyList);
        if (cItyList.size()>0){
            dataLIst.clear();
            for (CIty cIty:cItyList){
                dataLIst.add(cIty.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_CITY;

        }else {
            int provinceCode = selectedProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china/" + provinceCode;
            queryFromServer(address,"city");
        }
    }
    /**
     * 查询全国所有的县，优先从数据库查询，若果没有再到服务器查询
     */
    private void queryCounties() {
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        countyList = DataSupport.where("cityid = ?",String .valueOf(selectedCity.getId())).find(County.class);

        if (countyList.size()>0){
            dataLIst.clear();
            for (County county:countyList){
                dataLIst.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTY;

        }else {
            int provinceCode = selectedProvince.getProvinceCode();
            int cityCode = selectedCity.getCityCode();
            String address = "http://guolin.tech/api/china/" + provinceCode + "/" + cityCode;
            queryFromServer(address,"county");
        }

    }

    /**
     * 从服务器查数据
     * @param address
     * @param type
     */
    private void queryFromServer(String address, final String type) {
        Log.d(TAG, "queryFromServer: "+ address);
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Objects.requireNonNull(getActivity()).runOnUiThread(() -> {
                    closeProgressDialog();
                    Toast.makeText(getContext(),"加载失败",Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String responseText = Objects.requireNonNull(response.body()).string();
                boolean result = false;
                if ("province".equals(type)){
                    result = Utility.handleProvinceResponse(responseText);
                }else if ("city".equals(type)){
                    result = Utility.handleCityResponse(responseText,selectedProvince.getId());
                }else if ("county".equals(type)){
                    result = Utility.handleCountyResponse(responseText,selectedCity.getId());
                }
                if (result){
                    getActivity().runOnUiThread(() -> {
                        closeProgressDialog();
                        if ("province".equals(type)){
                            queryProvinces();
                        }else if ("city".equals(type)){
                            queryCities();
                        }else if ("county".equals(type)){
                            queryCounties();
                        }
                    });
                }
            }
        });
    }

    private void closeProgressDialog() {
        if (progressDialog != null){
            progressDialog.dismiss();
        }
    }

    private void showProgressDialog() {
        if(progressDialog == null){
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }
}
