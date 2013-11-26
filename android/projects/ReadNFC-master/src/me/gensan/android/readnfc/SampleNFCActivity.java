package me.gensan.android.readnfc;

import me.gensan.android.samplenfc.R;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.parse.GetCallback;
import com.parse.Parse;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;

/**
 * @author sumioka
 * 
 */
public class SampleNFCActivity extends Activity {

	private static final String TAG = "SampleNFCActivity";

	// メニューID
	private static final int MENU_FULL = 1;
	private static final int MENU_VACANCY = 2;
	private static final int MENU_SETTING = 3;

	// テスト用のID初期値
	private static final String SHOP_OBJCET_ID = "xaeOPV2RMx";
	private static final String FULL_NFC_ID = "127077fad9af9b";
	private static final String VACANCY_NFC_ID = "127077fad99c9b";

	private NfcAdapter mNfcAdapter;
	private PendingIntent mPendingIntent;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sample_nfc);

		// Parseを初期化
		Parse.initialize(this, "GDq21LZhuIl7jfqn1mzKQLpD2vlh9xQ5MWQgFA2P",
				"r7S62yjq28rscoIpM1fVJtzq12iPrBUz6P0RG1DT");

		// タグ接触によるアプリ起動時の処理
		Intent intent = getIntent();
		String action = intent.getAction();
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
				|| NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
				|| NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
			Log.d(TAG, "NFC DISCOVERD:" + action);
			String idm = getIdm(getIntent());
			if (idm != null) {
				onIdm(idm);
			}
		}
		mNfcAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
		mPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
				new Intent(getApplicationContext(), getClass())
						.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		// 起動後のタグ接触時の処理
		String idm = getIdm(intent);
		if (idm != null) {
			onIdm(idm);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mNfcAdapter != null) {
			setNfcIntentFilter(this, mNfcAdapter, mPendingIntent);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mNfcAdapter != null) {
			mNfcAdapter.disableForegroundDispatch(this);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_FULL, Menu.NONE, "手動で満席にする");
		menu.add(Menu.NONE, MENU_VACANCY, Menu.NONE, "手動で空き有にする");
		menu.add(Menu.NONE, MENU_SETTING, Menu.NONE, "ID設定");
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		//設定からObjectIDを取得
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		String objectId = prefs.getString("objectId", SHOP_OBJCET_ID);
		
		if (item.getItemId() == MENU_FULL) {
			Log.i(TAG, "Set FULL manually.");
			updateShopState(objectId, false);
		} else if (item.getItemId() == MENU_VACANCY) {
			Log.i(TAG, "Set VACANCY manually.");
			updateShopState(objectId, true);
		} else if (item.getItemId() == MENU_SETTING) {
			Intent intent = new Intent(this, NfcSettingActivity.class);
			startActivity(intent);
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Intentからidm取得
	 * 
	 * @param intent
	 * @return 取得したidm
	 */
	private String getIdm(Intent intent) {
		String idm = null;
		StringBuffer idmByte = new StringBuffer();
		byte[] rawIdm = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
		if (rawIdm != null) {
			for (int i = 0; i < rawIdm.length; i++) {
				idmByte.append(Integer.toHexString(rawIdm[i] & 0xff));
			}
			idm = idmByte.toString();
		}
		return idm;
	}

	/**
	 * IDMが取得できた時にショップ情報更新するかを決定
	 * 
	 * @param idm
	 */
	private void onIdm(String idm) {
		TextView idmView = (TextView) findViewById(R.id.idm);
		idmView.setText(idm);
		Log.i(TAG, "NEC read. IDM:" + idm);
		
		//設定から各種IDを取得
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		String objectId = prefs.getString("objectId", SHOP_OBJCET_ID);
		String fullId = prefs.getString("fullId", FULL_NFC_ID);
		String vacancyId = prefs.getString("vacancyId", VACANCY_NFC_ID);
		prefs.edit().putString("recentNfcId", idm).commit();
		
		Log.e(TAG, objectId);
		Log.e(TAG, fullId);
		Log.e(TAG, vacancyId);

		// 関連するIDならショップ情報更新
		if (idm.equals(FULL_NFC_ID)) {
			updateShopState(SHOP_OBJCET_ID, false);
		} else if (idm.equals(VACANCY_NFC_ID)) {
			updateShopState(SHOP_OBJCET_ID, true);
		}
	}

	/**
	 * ショップ情報を更新
	 * 
	 * @param objectId
	 *            店ごとの固有オブジェクトID
	 * @param vacancy
	 *            空き店舗状況(trueで空き)
	 */
	private void updateShopState(final String objectId, final boolean vacancy) {
		Log.i(TAG, "Update ObjectId:" + objectId + ", vacancy:" + vacancy);
		ParseQuery<ParseObject> query = ParseQuery.getQuery("shops");
		query.getInBackground(objectId, new GetCallback<ParseObject>() {
			@Override
			public void done(ParseObject shop, ParseException arg1) {
				shop.put("vacancy", vacancy);
				shop.saveInBackground();
			}
		});
	}

	private void setNfcIntentFilter(Activity activity, NfcAdapter nfcAdapter,
			PendingIntent seder) {
		IntentFilter typeNdef = new IntentFilter(
				NfcAdapter.ACTION_NDEF_DISCOVERED);
		try {
			typeNdef.addDataType("*/*");
		} catch (MalformedMimeTypeException e) {
			e.printStackTrace();
		}
		IntentFilter httpNdef = new IntentFilter(
				NfcAdapter.ACTION_NDEF_DISCOVERED);
		httpNdef.addDataScheme("http");
		IntentFilter[] filters = new IntentFilter[] { typeNdef, httpNdef };
		String[][] techLists = new String[][] {
				new String[] { IsoDep.class.getName() },
				new String[] { NfcA.class.getName() },
				new String[] { NfcB.class.getName() },
				new String[] { NfcF.class.getName() },
				new String[] { NfcV.class.getName() },
				new String[] { Ndef.class.getName() },
				new String[] { NdefFormatable.class.getName() },
				new String[] { MifareClassic.class.getName() },
				new String[] { MifareUltralight.class.getName() } };
		nfcAdapter
				.enableForegroundDispatch(activity, seder, filters, techLists);
	}
}