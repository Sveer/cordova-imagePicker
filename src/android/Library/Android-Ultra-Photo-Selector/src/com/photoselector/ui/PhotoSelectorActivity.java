package com.photoselector.ui;
/**
 *
 * @author Aizaz AZ
 *
 */
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiscCache;
import com.nostra13.universalimageloader.cache.disc.naming.HashCodeFileNameGenerator;
import com.nostra13.universalimageloader.cache.memory.impl.LruMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;
import com.nostra13.universalimageloader.core.decode.BaseImageDecoder;
import com.nostra13.universalimageloader.core.download.BaseImageDownloader;
import com.nostra13.universalimageloader.utils.StorageUtils;
import com.synconset.FakeR;
import com.photoselector.domain.PhotoSelectorDomain;
import com.photoselector.model.AlbumModel;
import com.photoselector.model.PhotoModel;
import com.photoselector.ui.PhotoItem.onItemClickListener;
import com.photoselector.ui.PhotoItem.onPhotoItemCheckedListener;
import com.photoselector.util.AnimationUtil;
import com.photoselector.util.CommonUtils;

/**
 * @author Aizaz AZ
 *
 */
public class PhotoSelectorActivity extends Activity implements
		onItemClickListener, onPhotoItemCheckedListener, OnItemClickListener,
		OnClickListener {

	private FakeR fakeR;
	public static final int SINGLE_IMAGE = 1;
	public static final String KEY_MAX = "MAX_IMAGES";
	public static final String WIDTH_KEY = "WIDTH";
	public static final String HEIGHT_KEY = "HEIGHT";
	public static final String QUALITY_KEY = "QUALITY";
	public static final String PRE_SELECTED_ASSETS = "PRE_SELECTED_ASSETS";
	private int MAX_IMAGE;

	public static final int REQUEST_PHOTO = 0;
	private static final int REQUEST_CAMERA = 1;

	public static String RECCENT_PHOTO = null;

	private GridView gvPhotos;
	private ListView lvAblum;
	private Button btnOk;
	private TextView tvAlbum, tvPreview, tvTitle;
	private PhotoSelectorDomain photoSelectorDomain;
	private PhotoSelectorAdapter photoAdapter;
	private AlbumAdapter albumAdapter;
	private RelativeLayout layoutAlbum;
	private ArrayList<String> preSelectedAssets;
	private ArrayList<PhotoModel> selected;
	private TextView tvNumber;
	private int imageOrder=0;
	private int desiredWidth;
	private int desiredHeight;
	private int quality;

	private ProgressDialog progress;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		fakeR = new FakeR(this);
		RECCENT_PHOTO = getResources().getString(fakeR.getId("string", "recent_photos"));
		requestWindowFeature(Window.FEATURE_NO_TITLE);// 鍘绘帀鏍囬�鏍�
		setContentView(fakeR.getId("layout", "activity_photoselector"));

		if (getIntent().getExtras() != null) {
			MAX_IMAGE = getIntent().getIntExtra(KEY_MAX, 10);
			desiredWidth = getIntent().getIntExtra(WIDTH_KEY, 0);
			desiredHeight = getIntent().getIntExtra(HEIGHT_KEY, 0);
			quality = getIntent().getIntExtra(QUALITY_KEY, 0);
			preSelectedAssets = getIntent().getStringArrayListExtra(PRE_SELECTED_ASSETS);
		}

		initImageLoader();

		photoSelectorDomain = new PhotoSelectorDomain(getApplicationContext());

		selected = new ArrayList<PhotoModel>();

		tvTitle = (TextView) findViewById(fakeR.getId("id", "tv_title_lh"));
		gvPhotos = (GridView) findViewById(fakeR.getId("id", "gv_photos_ar"));
		lvAblum = (ListView) findViewById(fakeR.getId("id", "lv_ablum_ar"));
		btnOk = (Button) findViewById(fakeR.getId("id", "btn_right_lh"));
		tvAlbum = (TextView) findViewById(fakeR.getId("id", "tv_album_ar"));
		tvPreview = (TextView) findViewById(fakeR.getId("id", "tv_preview_ar"));
		layoutAlbum = (RelativeLayout) findViewById(fakeR.getId("id", "layout_album_ar"));
		tvNumber = (TextView) findViewById(fakeR.getId("id", "tv_number"));

		btnOk.setOnClickListener(this);
		tvAlbum.setOnClickListener(this);
		tvPreview.setOnClickListener(this);

		for(String selectedAsset:preSelectedAssets) {
			PhotoModel m = new PhotoModel(selectedAsset);
			onCheckedChanged(m, null, true);
		}

//		tvNumber.setText("(" + selected.size() + ")");

		photoAdapter = new PhotoSelectorAdapter(getApplicationContext(),
				new ArrayList<PhotoModel>(), CommonUtils.getWidthPixels(this),
				this, this, this);
		gvPhotos.setAdapter(photoAdapter);

		albumAdapter = new AlbumAdapter(getApplicationContext(),
				new ArrayList<AlbumModel>());
		lvAblum.setAdapter(albumAdapter);
		lvAblum.setOnItemClickListener(this);

		findViewById(fakeR.getId("id", "bv_back_lh")).setOnClickListener(this); // 杩斿洖

		photoSelectorDomain.getReccent(reccentListener); // 鏇存柊鏈�杩戠収鐗�
		photoSelectorDomain.updateAlbum(albumListener); // 璺熸柊鐩稿唽淇℃伅
		progress = new ProgressDialog(this);
		progress.setCanceledOnTouchOutside(false);
		progress.setCancelable(false);
		progress.setTitle(fakeR.getId("string", "progress_title"));
		progress.setMessage(getString(fakeR.getId("string", "progress_message")));
	}

	private void initImageLoader() {
		CommonUtils.displayImageOptions = new DisplayImageOptions.Builder()
				.showImageOnLoading(fakeR.getId("drawable", "ic_picture_loading"))
				.showImageOnFail(fakeR.getId("drawable", "ic_picture_loadfailed"))
				.cacheInMemory(true).cacheOnDisk(true)
				.resetViewBeforeLoading(true).considerExifParams(true)
				.bitmapConfig(Bitmap.Config.RGB_565).build();

		CommonUtils.config = new ImageLoaderConfiguration.Builder(
				this)
				.memoryCacheExtraOptions(400, 400)
						// default = device screen dimensions
				.diskCacheExtraOptions(400, 400, null)
				.threadPoolSize(5)
						// default Thread.NORM_PRIORITY - 1
				.threadPriority(Thread.NORM_PRIORITY)
						// default FIFO
				.tasksProcessingOrder(QueueProcessingType.LIFO)
						// default
				.denyCacheImageMultipleSizesInMemory()
				.memoryCache(new LruMemoryCache(2 * 1024 * 1024))
				.memoryCacheSize(2 * 1024 * 1024)
				.memoryCacheSizePercentage(13)
						// default
				.diskCache(
						new UnlimitedDiscCache(StorageUtils.getCacheDirectory(
								this, true)))
						// default
				.diskCacheSize(50 * 1024 * 1024).diskCacheFileCount(100)
				.diskCacheFileNameGenerator(new HashCodeFileNameGenerator())
						// default
				.imageDownloader(new BaseImageDownloader(this))
						// default
				.imageDecoder(new BaseImageDecoder(false))
				.defaultDisplayImageOptions(CommonUtils.displayImageOptions).build();

		ImageLoader.getInstance().init(CommonUtils.config);
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == fakeR.getId("id", "btn_right_lh"))
			ok(); // 閫夊畬鐓х墖
		else if (v.getId() == fakeR.getId("id", "tv_album_ar"))
			album();
		else if (v.getId() == fakeR.getId("id", "tv_preview_ar"))
			priview();
		else if (v.getId() == fakeR.getId("id", "tv_camera_vc"))
			catchPicture();
		else if (v.getId() == fakeR.getId("id", "bv_back_lh"))
			finish();
	}

	/** 鎷嶇収 */
	private void catchPicture() {
		CommonUtils.launchActivityForResult(this, new Intent(
				MediaStore.ACTION_IMAGE_CAPTURE), REQUEST_CAMERA);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CAMERA && resultCode == RESULT_OK) {
			PhotoModel photoModel = new PhotoModel(CommonUtils.query(
					getApplicationContext(), data.getData()));
			// selected.clear();
			// //--keep all
			// selected photos
			// tvNumber.setText("(0)");
			// //--keep all
			// selected photos
			// ///////////////////////////////////////////////////////////////////////////////////////////
			if (selected.size() > MAX_IMAGE) {
				Toast.makeText(
						this,
						String.format(
								getString(fakeR.getId("string", "max_img_limit_reached")),
								MAX_IMAGE), Toast.LENGTH_SHORT).show();
				photoModel.setChecked(false);
				photoAdapter.notifyDataSetChanged();
			} else {
				if (!selected.contains(photoModel)) {
					selected.add(photoModel);
				}
			}
			ok();
		}
	}

	/** 瀹屾垚 */
	private void ok() {
		if (selected.isEmpty()) {
			setResult(RESULT_CANCELED);
		} else {
			progress.show();
			new ResizeImagesTask().execute(selected);
			/*
			Intent data = new Intent();
			Bundle bundle = new Bundle();
			bundle.putSerializable("photos", selected);
			data.putExtras(bundle);
			setResult(RESULT_OK, data);
			*/
		}
	}

	/** 棰勮�鐓х墖 */
	private void priview() {
		Bundle bundle = new Bundle();
		bundle.putSerializable("photos", selected);
		CommonUtils.launchActivity(this, PhotoPreviewActivity.class, bundle);
	}

	private void album() {
		if (layoutAlbum.getVisibility() == View.GONE) {
			popAlbum();
		} else {
			hideAlbum();
		}
	}

	/** 寮瑰嚭鐩稿唽鍒楄〃 */
	private void popAlbum() {
		layoutAlbum.setVisibility(View.VISIBLE);
		new AnimationUtil(getApplicationContext(), fakeR.getId("anim", "translate_up_current"))
				.setLinearInterpolator().startAnimation(layoutAlbum);
	}

	/** 闅愯棌鐩稿唽鍒楄〃 */
	private void hideAlbum() {
		new AnimationUtil(getApplicationContext(), fakeR.getId("anim", "translate_down"))
				.setLinearInterpolator().startAnimation(layoutAlbum);
		layoutAlbum.setVisibility(View.GONE);
	}

	/** 娓呯┖閫変腑鐨勫浘鐗� */
	private void reset() {
		selected.clear();
		tvNumber.setText("(0)");
		tvPreview.setEnabled(false);
	}

	@Override
	/** 鐐瑰嚮鏌ョ湅鐓х墖 */
	public void onItemClick(int position) {
		Bundle bundle = new Bundle();
		if (tvAlbum.getText().toString().equals(RECCENT_PHOTO))
			bundle.putInt("position", position - 1);
		else
			bundle.putInt("position", position);
		bundle.putString("album", tvAlbum.getText().toString());
		CommonUtils.launchActivity(this, PhotoPreviewActivity.class, bundle);
	}

	@Override
	/** 鐓х墖閫変腑鐘舵�佹敼鍙樹箣鍚� */
	public void onCheckedChanged(PhotoModel photoModel,
								 CompoundButton buttonView, boolean isChecked) {
		if (isChecked) {
			if(imageOrder>MAX_IMAGE)
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				Resources res = getResources();
				String title = String.format(res.getString(fakeR.getId("string", "alert_limit_title")), MAX_IMAGE);
				String message = String.format(res.getString(fakeR.getId("string", "alert_limit_message")), MAX_IMAGE);
				builder.setTitle(title);
				builder.setMessage(message);
				builder.setPositiveButton(res.getString(fakeR.getId("string", "ok")), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
				});
				AlertDialog alert = builder.create();
				alert.show();
				buttonView.toggle();
				imageOrder--;
				selected.remove(photoModel);
			}
			else
			{
				if (!selected.contains(photoModel))
				{
					photoModel.setOrder(imageOrder);
					selected.add(photoModel);
					imageOrder++;
				}
				tvPreview.setEnabled(true);
			}
		} else {
			PhotoModel photomodel=null;
			for (int i=0; i<selected.size();i++){
				if(photomodel!=null)
				{
					selected.get(i).setOrder(selected.get(i).getOrder()-1);
				}
				if(selected.get(i).equals(photoModel))
				{
					photomodel = selected.get(i);
				}
			}
			if(photomodel!=null)
			{
				selected.remove(photomodel);
				imageOrder--;
			}
			//selected.remove(photoModel);
		}
		tvNumber.setText("(" + selected.size() + ")");
		if (selected.isEmpty()) {
			tvPreview.setEnabled(false);
			tvPreview.setText(getString(fakeR.getId("string", "preview")));
		}
	}

	@Override
	public void onBackPressed() {
		if (layoutAlbum.getVisibility() == View.VISIBLE) {
			hideAlbum();
		} else
			super.onBackPressed();
	}

	@Override
	/** 鐩稿唽鍒楄〃鐐瑰嚮浜嬩欢 */
	public void onItemClick(AdapterView<?> parent, View view, int position,
							long id) {
		AlbumModel current = (AlbumModel) parent.getItemAtPosition(position);
		for (int i = 0; i < parent.getCount(); i++) {
			AlbumModel album = (AlbumModel) parent.getItemAtPosition(i);
			if (i == position)
			{
				album.setCheck(true);
			}
			else
				album.setCheck(false);
		}
		albumAdapter.notifyDataSetChanged();
		hideAlbum();
		tvAlbum.setText(current.getName());
		// tvTitle.setText(current.getName());

		// 鏇存柊鐓х墖鍒楄〃
		if (current.getName().equals(RECCENT_PHOTO))
			photoSelectorDomain.getReccent(reccentListener);
		else
			photoSelectorDomain.getAlbum(current.getName(), reccentListener); // 鑾峰彇閫変腑鐩稿唽鐨勭収鐗�
	}

	/** 鑾峰彇鏈�湴鍥惧簱鐓х墖鍥炶皟 */
	public interface OnLocalReccentListener {
		public void onPhotoLoaded(List<PhotoModel> photos);
		public void onPhotoAdded(List<PhotoModel> photos);
	}

	/** 鑾峰彇鏈�湴鐩稿唽淇℃伅鍥炶皟 */
	public interface OnLocalAlbumListener {
		public void onAlbumLoaded(List<AlbumModel> albums);
	}

	private OnLocalAlbumListener albumListener = new OnLocalAlbumListener() {
		@Override
		public void onAlbumLoaded(List<AlbumModel> albums) {
			albumAdapter.update(albums);
		}
	};

	private OnLocalReccentListener reccentListener = new OnLocalReccentListener() {
		@Override
		public void onPhotoLoaded(List<PhotoModel> photos) {
			for (PhotoModel model : photos) {
				if (selected.contains(model)) {
					model.setChecked(true);
				}
			}
			photoAdapter.update(photos);
			gvPhotos.smoothScrollToPosition(0); // 婊氬姩鍒伴《绔�
			// reset(); //--keep selected photos

		}
		@Override
		public void onPhotoAdded(List<PhotoModel> photos) {
			for (PhotoModel model : photos) {
				if (selected.contains(model)) {
					model.setChecked(true);
				}
			}
			photoAdapter.add(photos);
			//gvPhotos.smoothScrollToPosition(0); // 婊氬姩鍒伴《绔�
			// reset(); //--keep selected photos

		}
	};


	private class ResizeImagesTask extends AsyncTask<ArrayList<PhotoModel>, Void, Map<String,ArrayList<String>>> {
		private Exception asyncTaskError = null;

		@Override
		protected Map<String,ArrayList<String>> doInBackground(ArrayList<PhotoModel>... fileSets) {
			ArrayList<PhotoModel> fileNames = fileSets[0];
			Bitmap bmp = null;
			int rotate = 0;
			String filename=null;
			ArrayList<String> al = new ArrayList<String>();
			ArrayList<String> selectedAssets  = new ArrayList<String>();
			ArrayList<String> invalidImages  = new ArrayList<String>();
			try {
				for(int i=0; i<fileNames.size();i++)
				{
					if(i!=fileNames.get(i).getOrder())
					{
						for(int j=0; j<fileNames.size();j++)
						{
							if(i==fileNames.get(j).getOrder())
							{
								filename=fileNames.get(j).getOriginalPath();
								rotate = fileNames.get(j).getRotation();
								break;
							}
						}
					}
					else
					{
						filename=fileNames.get(i).getOriginalPath();
						rotate = fileNames.get(i).getRotation();
					}
					int index = filename.lastIndexOf('.');
					String ext = filename.substring(index);
					if (ext.compareToIgnoreCase(".gif") != 0) {
						filename = filename.replaceAll("file://", "");
						File file = new File(filename);
						BitmapFactory.Options options = new BitmapFactory.Options();
						options.inSampleSize = 1;
						options.inJustDecodeBounds = true;
						BitmapFactory.decodeFile(file.getAbsolutePath(), options);
						int width = options.outWidth;
						int height = options.outHeight;
						float scale = calculateScale(width, height);
						if (scale < 1) {
							int finalWidth = (int)(width * scale);
							int finalHeight = (int)(height * scale);
							int inSampleSize = calculateInSampleSize(options, finalWidth, finalHeight);
							options = new BitmapFactory.Options();
							options.inSampleSize = inSampleSize;
							try {
								try {
									bmp = this.tryToGetBitmap(file, options, rotate, true);
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							} catch (OutOfMemoryError e) {
								options.inSampleSize = calculateNextSampleSize(options.inSampleSize);
								try {
									try {
										bmp = this.tryToGetBitmap(file, options, rotate, false);
									} catch (IOException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}
								} catch (OutOfMemoryError e2) {
									throw new IOException("Unable to load image into memory.");
								}
							}
							file = this.storeImage(bmp, file);
						}
						selectedAssets.add(filename);
						al.add(Uri.fromFile(file).toString());
					} else {
						invalidImages.add(filename);
					}
				}
				Map<String, ArrayList<String>> result = new HashMap<String, ArrayList<String>>();
				result.put("images", al);
				result.put("preSelectedAssets", selectedAssets);
				if(invalidImages.size() > 0) {
					result.put("invalidImages", invalidImages);
				}
				return result;
			} catch(IOException e) {
				try {
					asyncTaskError = e;
					for (int i = 0; i < al.size(); i++) {
						URI uri = new URI(al.get(i));
						File file = new File(uri);
						file.delete();
					}
				} catch(Exception exception) {
					// the finally does what we want to do
				} finally {
					return new HashMap<String, ArrayList<String>>();
				}
			}
		}

		@Override
		protected void onPostExecute(Map<String, ArrayList<String>> result) {
			Intent data = new Intent();

			if (asyncTaskError != null) {
				Bundle res = new Bundle();
				res.putString("ERRORMESSAGE", asyncTaskError.getMessage());
				data.putExtras(res);
				setResult(RESULT_CANCELED, data);
			} else if (result.size() > 0) {
				Bundle res = new Bundle();
				res.putStringArrayList("MULTIPLEFILENAMES", result.get("images"));
				res.putStringArrayList("SELECTED_ASSETS", result.get("preSelectedAssets"));
				res.putStringArrayList("INVALID_IMAGES", result.get("invalidImages"));
				if (selected != null) {
					res.putInt("TOTALFILES", selected.size());
				}
				data.putExtras(res);
				setResult(RESULT_OK, data);
			} else {
				setResult(RESULT_CANCELED, data);
			}
			progress.dismiss();
			finish();
		}

		private Bitmap tryToGetBitmap(File file, BitmapFactory.Options options, int rotate, boolean shouldScale) throws IOException, OutOfMemoryError {
			Bitmap bmp;
			if (options == null) {
				bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
			} else {
				bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
			}
			if (bmp == null) {
				throw new IOException("The image file could not be opened.");
			}
			if (options != null && shouldScale) {
				float scale = calculateScale(options.outWidth, options.outHeight);
				bmp = this.getResizedBitmap(bmp, scale);
			}
			if (rotate != 0) {
				Matrix matrix = new Matrix();
				matrix.setRotate(rotate);
				bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
			}
			return bmp;
		}

		/*
		* The following functions are originally from
		* https://github.com/raananw/PhoneGap-Image-Resizer
		* 
		* They have been modified by Andrew Stephan for Sync OnSet
		*
		* The software is open source, MIT Licensed.
		* Copyright (C) 2012, webXells GmbH All Rights Reserved.
		*/
		private File storeImage(Bitmap bmp, File file) throws IOException {
			String fileName = file.getName();
			// int index = fileName.lastIndexOf('.');
			// String name = "Temp_" + fileName.substring(0, index).replaceAll("([^a-zA-Z0-9])", "");
			String name = "Temp_" + fileName;
			// String ext = fileName.substring(index);
			// File file = File.createTempFile(name, ext);
			String tDir = System.getProperty("java.io.tmpdir");
			String fullFilePath = tDir + "/" + name;
			file = new File(fullFilePath);
			OutputStream outStream = new FileOutputStream(file);
			bmp.compress(Bitmap.CompressFormat.JPEG, quality, outStream);
			outStream.flush();
			outStream.close();
			return file;
		}

		private Bitmap getResizedBitmap(Bitmap bm, float factor) {
			int width = bm.getWidth();
			int height = bm.getHeight();
			// create a matrix for the manipulation
			Matrix matrix = new Matrix();
			// resize the bit map
			matrix.postScale(factor, factor);
			// recreate the new Bitmap
			Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
			return resizedBitmap;
		}
	}

	private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {
			final int halfHeight = height / 2;
			final int halfWidth = width / 2;

			// Calculate the largest inSampleSize value that is a power of 2 and keeps both
			// height and width larger than the requested height and width.
			while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
				inSampleSize *= 2;
			}
		}

		return inSampleSize;
	}

	private int calculateNextSampleSize(int sampleSize) {
		double logBaseTwo = (int)(Math.log(sampleSize) / Math.log(2));
		return (int)Math.pow(logBaseTwo + 1, 2);
	}

	private float calculateScale(int width, int height) {
		float widthScale = 1.0f;
		float heightScale = 1.0f;
		float scale = 1.0f;
		if (desiredWidth > 0 || desiredHeight > 0) {
			if (desiredHeight == 0 && desiredWidth < width) {
				scale = (float)desiredWidth/width;
			} else if (desiredWidth == 0 && desiredHeight < height) {
				scale = (float)desiredHeight/height;
			} else {
				if (desiredWidth > 0 && desiredWidth < width) {
					widthScale = (float)desiredWidth/width;
				}
				if (desiredHeight > 0 && desiredHeight < height) {
					heightScale = (float)desiredHeight/height;
				}
				if (widthScale < heightScale) {
					scale = widthScale;
				} else {
					scale = heightScale;
				}
			}
		}

		return scale;
	}
}