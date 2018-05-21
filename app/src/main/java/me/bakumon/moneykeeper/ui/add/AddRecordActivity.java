package me.bakumon.moneykeeper.ui.add;

import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.bakumon.moneykeeper.Injection;
import me.bakumon.moneykeeper.R;
import me.bakumon.moneykeeper.base.BaseActivity;
import me.bakumon.moneykeeper.database.entity.Record;
import me.bakumon.moneykeeper.database.entity.RecordType;
import me.bakumon.moneykeeper.database.entity.RecordWithType;
import me.bakumon.moneykeeper.databinding.ActivityAddRecordBinding;
import me.bakumon.moneykeeper.utill.DateUtils;
import me.bakumon.moneykeeper.utill.SoftInputUtils;
import me.bakumon.moneykeeper.utill.ToastUtils;
import me.bakumon.moneykeeper.viewmodel.ViewModelFactory;

/**
 * HomeActivity
 *
 * @author bakumon https://bakumon.me
 * @date 2018/4/9
 */
public class AddRecordActivity extends BaseActivity {

    private static final String TAG = AddRecordActivity.class.getSimpleName();
    public static final String KEY_RECORD_BEAN = "AddTypeActivity.key_record_bean";

    private ActivityAddRecordBinding mBinding;

    private AddRecordViewModel mViewModel;

    private Date mCurrentChooseDate = DateUtils.getTodayDate();
    private Calendar mCurrentChooseCalendar = Calendar.getInstance();
    private int mCurrentType;

    private RecordWithType mRecord;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_add_record;
    }

    @Override
    protected void onInit(@Nullable Bundle savedInstanceState) {
        mBinding = getDataBinding();
        ViewModelFactory viewModelFactory = Injection.provideViewModelFactory(this);
        mViewModel = ViewModelProviders.of(this, viewModelFactory).get(AddRecordViewModel.class);

        initView();
        initData();
    }

    private void initData() {
        initRecordTypes();
    }

    private void initView() {
        mRecord = (RecordWithType) getIntent().getSerializableExtra(KEY_RECORD_BEAN);

        mBinding.titleBar.ibtClose.setBackgroundResource(R.drawable.ic_close);
        mBinding.titleBar.ibtClose.setOnClickListener(v -> finish());

        mBinding.edtRemark.setOnEditorActionListener((v, actionId, event) -> {
            SoftInputUtils.hideSoftInput(mBinding.typePageOutlay);
            mBinding.keyboard.setEditTextFocus();
            return false;
        });

        if (mRecord == null) {
            mCurrentType = RecordType.TYPE_OUTLAY;
            mBinding.titleBar.setTitle(getString(R.string.text_add_record));
        } else {
            mCurrentType = mRecord.mRecordTypes.get(0).type;
            mBinding.titleBar.setTitle(getString(R.string.text_modify_record));
            mBinding.edtRemark.setText(mRecord.remark);
            mBinding.keyboard.setText(mRecord.money.toPlainString());
            mCurrentChooseDate = mRecord.time;
            mCurrentChooseCalendar.setTime(mCurrentChooseDate);
            mBinding.qmTvDate.setText(DateUtils.getWordTime(mCurrentChooseDate));
        }

        mBinding.keyboard.setAffirmClickListener(text -> {
            if (mRecord == null) {
                insertRecord(text);
            } else {
                modifyRecord(text);
            }
        });

        mBinding.qmTvDate.setOnClickListener(v -> {
            DatePickerDialog dpd = DatePickerDialog.newInstance(
                    (view, year, monthOfYear, dayOfMonth) -> {
                        mCurrentChooseDate = DateUtils.getDate(year, monthOfYear + 1, dayOfMonth);
                        mCurrentChooseCalendar.setTime(mCurrentChooseDate);
                        mBinding.qmTvDate.setText(DateUtils.getWordTime(mCurrentChooseDate));
                    }, mCurrentChooseCalendar);
            dpd.setMaxDate(Calendar.getInstance());
            dpd.show(getFragmentManager(), "Datepickerdialog");
        });
        mBinding.typeChoice.rgType.setOnCheckedChangeListener((group, checkedId) -> {

            if (checkedId == R.id.rb_outlay) {
                mCurrentType = RecordType.TYPE_OUTLAY;
                mBinding.typePageOutlay.setVisibility(View.VISIBLE);
                mBinding.typePageIncome.setVisibility(View.GONE);
            } else {
                mCurrentType = RecordType.TYPE_INCOME;
                mBinding.typePageOutlay.setVisibility(View.GONE);
                mBinding.typePageIncome.setVisibility(View.VISIBLE);
            }

        });
    }

    private void insertRecord(String text) {
        // 防止重复提交
        mBinding.keyboard.setAffirmEnable(false);
        Record record = new Record();
        record.money = new BigDecimal(text);
        record.remark = mBinding.edtRemark.getText().toString().trim();
        record.time = mCurrentChooseDate;
        record.createTime = new Date();
        record.recordTypeId = mCurrentType == RecordType.TYPE_OUTLAY ?
                mBinding.typePageOutlay.getCurrentItem().id : mBinding.typePageIncome.getCurrentItem().id;

        mDisposable.add(mViewModel.insertRecord(record)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::finish,
                        throwable -> {
                            Log.e(TAG, "新增记录失败", throwable);
                            mBinding.keyboard.setAffirmEnable(true);
                            ToastUtils.show(R.string.toast_add_record_fail);
                        }
                ));
    }

    private void modifyRecord(String text) {
        // 防止重复提交
        mBinding.keyboard.setAffirmEnable(false);
        mRecord.money = new BigDecimal(text);
        mRecord.remark = mBinding.edtRemark.getText().toString().trim();
        mRecord.time = mCurrentChooseDate;
        mRecord.recordTypeId = mCurrentType == RecordType.TYPE_OUTLAY ?
                mBinding.typePageOutlay.getCurrentItem().id : mBinding.typePageIncome.getCurrentItem().id;

        mDisposable.add(mViewModel.updateRecord(mRecord)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::finish,
                        throwable -> {
                            Log.e(TAG, "记录修改失败", throwable);
                            mBinding.keyboard.setAffirmEnable(true);
                            ToastUtils.show(R.string.toast_modify_record_fail);
                        }
                ));
    }

    private void initRecordTypes() {
        mDisposable.add(mViewModel.initRecordTypes()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::getAllRecordTypes,
                        throwable -> {
                            ToastUtils.show(R.string.toast_init_types_fail);
                            Log.e(TAG, "初始化类型数据失败", throwable);
                        }));
    }

    private void getAllRecordTypes() {
        mDisposable.add(mViewModel.getAllRecordTypes()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((recordTypes) -> {
                    mBinding.typePageOutlay.setNewData(recordTypes, RecordType.TYPE_OUTLAY);
                    mBinding.typePageIncome.setNewData(recordTypes, RecordType.TYPE_INCOME);

                    if (mCurrentType == RecordType.TYPE_OUTLAY) {
                        mBinding.typeChoice.rgType.check(R.id.rb_outlay);
                        mBinding.typePageOutlay.initCheckItem(mRecord);
                    } else {
                        mBinding.typeChoice.rgType.check(R.id.rb_income);
                        mBinding.typePageIncome.initCheckItem(mRecord);
                    }

                }, throwable -> {
                    ToastUtils.show(R.string.toast_get_types_fail);
                    Log.e(TAG, "获取类型数据失败", throwable);
                }));
    }
}